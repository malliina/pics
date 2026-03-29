package com.malliina.pics.db

import cats.implicits.*
import com.malliina.database.DoobieDatabase
import com.malliina.pics.db.PicsDatabase.log
import com.malliina.pics.{Access, Key, KeyMeta, KeyNotFound, Language, MetaSourceT, PicUsername, Role, UserDatabase}
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, NonNeg, UserId}
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.util.fragment.Fragment

object PicsDatabase:
  private val log = AppLogger(getClass)

class PicsDatabase[F[_]](db: DoobieDatabase[F]) extends MetaSourceT[F] with UserDatabase[F]:
  override def meta(key: Key): F[KeyMeta] = db.run:
    keyQuery(key).option.flatMap: opt =>
      opt.map(pure).getOrElse(fail(KeyNotFound(key)))

  def load(offset: NonNeg, limit: NonNeg, user: PicUsername): F[List[KeyMeta]] = db.run:
    val frag = fr"""and u.username = $user
                    order by p.added desc limit $limit offset $offset
                 """
    metaQuery(frag)
      .to[List]

  def saveMeta(key: Key, owner: PicUsername): F[KeyMeta] = db.run:
    val access = if owner == PicUsername.anon then Access.Public else Access.Private
    for
      user <- fetchOrCreateUser(owner)
      _ <- sql"insert into pics(`key`, user, access) values ($key, ${user.id}, $access)".update.run
      row <- metaQuery(fr"and p.`key` = $key and u.username = $owner").unique
    yield
      log.info(s"Inserted '$key' by '$owner'.")
      row

  def remove(key: Key, user: PicUsername): F[Boolean] = db.run:
    sql"delete from pics where `key` = $key and user = (select id from users where username = $user)".update.run
      .map: deleted =>
        val wasDeleted = deleted > 0
        if wasDeleted then log.info(s"Deleted '$key' by '$user'.")
        else log.warn(s"Tried to remove '$key' by '$user' but found no matching rows.")
        wasDeleted

  override def modify(key: Key, user: PicUsername, access: Access): F[KeyMeta] = db.run:
    val owns = sql"""select exists(select p.`key`
                                   from pics p, users u
                                   where p.user = u.id and p.`key` = $key and u.username = $user)"""
      .query[Boolean]
      .unique
    for
      ownsPic <- owns
      _ <- if !ownsPic then fail(KeyNotFound(key)) else pure(())
      _ <-
        sql"update pics set access = $access where `key` = $key and user = (select id from users where username = $user)".update.run
      row <- metaRow(key)
    yield
      log.info(s"User $user changed access of $key to $access.")
      row

  def contains(key: Key): F[Boolean] = db.run:
    existsQuery(key)

  private def existsQuery(key: Key) =
    sql"select exists(select `key` from pics where `key` = $key)".query[Boolean].unique

  def putMetaIfNotExists(meta: KeyMeta): F[Int] = db.run:
    val q = existsQuery(meta.key)
    q.flatMap: exists =>
      if exists then pure(0)
      else
        for
          user <- fetchOrCreateUser(meta.owner)
          rows <-
            sql"insert into pics(`key`, user, access, added) values(${meta.key}, ${user.id}, ${meta.access}, ${meta.added})".update.run
        yield rows

  def userByToken(token: AccessToken): F[Option[UserRow]] = db.run:
    sql"""select u.id, u.username, u.role, u.language, u.added
          from users u, tokens t
          where t.user = u.id and t.token = $token"""
      .query[UserRow]
      .option

  override def user(name: PicUsername): F[Option[UserRow]] = db.run:
    userByName(name)

  private def userByName(name: PicUsername) =
    sql"""select u.id, u.username, u.role, u.language, u.added
          from users u
          where u.username = $name
       """.query[UserRow].option

  private def userById(id: UserId) =
    sql"""select u.id, u.username, u.role, u.language, u.added
          from users u
          where u.id = $id
       """.query[UserRow]

  private def metaRow(key: Key) = keyQuery(key).unique

  private def keyQuery(key: Key) = metaQuery(fr"and p.`key` = $key")

  private def metaQuery(fragment: Fragment) =
    sql"""select p.`key`, u.username, p.access, p.added
          from pics p, users u
          where p.user = u.id $fragment""".query[KeyMeta]

  private def fetchOrCreateUser(name: PicUsername): ConnectionIO[UserRow] =
    for
      userOpt <- userByName(name)
      user <- userOpt
        .map(pure)
        .getOrElse(insertUserIO(name))
    yield user

  private def insertUserIO(name: PicUsername): ConnectionIO[UserRow] =
    for
      id <- sql"""insert into users(username, role, language)
                  values ($name, ${Role.default}, ${Language.default})""".update
        .withUniqueGeneratedKeys[UserId]("id")
      row <- userById(id).unique
    yield row

  private def pure[T](t: T): ConnectionIO[T] = t.pure[ConnectionIO]
  private def fail[A](e: Exception): ConnectionIO[A] = e.raiseError[ConnectionIO, A]
