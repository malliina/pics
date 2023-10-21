package com.malliina.pics.db

import cats.implicits.*
import com.malliina.database.DoobieDatabase
import com.malliina.pics.db.PicsDatabase.log
import com.malliina.pics.{Access, Key, KeyMeta, MetaSourceT, PicOwner, UserDatabase}
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, UserId, Username}
import doobie.ConnectionIO
import doobie.implicits.*

object PicsDatabase:
  private val log = AppLogger(getClass)

class PicsDatabase[F[_]](db: DoobieDatabase[F]) extends MetaSourceT[F] with UserDatabase[F]:
  override def meta(key: Key): F[KeyMeta] = db.run:
    metaQuery(key)

  private def metaQuery(key: Key) =
    sql"""select p.`key`, u.username, p.access, p.added
          from pics p, users u
          where p.user = u.id and p.`key` = $key""".query[KeyMeta].unique

  def load(offset: Int, limit: Int, user: PicOwner): F[List[KeyMeta]] = db.run:
    sql"""select p.`key`, u.username, p.access, p.added
          from pics p, users u
          where p.user = u.id and u.username = $user
          order by p.added desc limit $limit offset $offset"""
      .query[KeyMeta]
      .to[List]

  def saveMeta(key: Key, owner: PicOwner): F[KeyMeta] = db.run:
    val access = if owner == PicOwner.anon then Access.Public else Access.Private
    for
      userId <- fetchOrCreateUser(owner)
      _ <- sql"insert into pics(`key`, user, access) values ($key, $userId, $access)".update.run
      row <-
        sql"""select p.`key`, u.username, p.access, p.added
              from pics p, users u
              where p.user = u.id and p.`key` = $key and u.username = $owner"""
          .query[KeyMeta]
          .unique
    yield
      log.info(s"Inserted '$key' by '$owner'.")
      row

  def remove(key: Key, user: PicOwner): F[Boolean] = db.run:
    sql"delete from pics where `key` = $key and user = (select id from users where username = $user)".update.run
      .map: deleted =>
        val wasDeleted = deleted > 0
        if wasDeleted then log.info(s"Deleted '$key' by '$user'.")
        else log.warn(s"Tried to remove '$key' by '$user' but found no matching rows.")
        wasDeleted

  override def modify(key: Key, user: PicOwner, access: Access): F[KeyMeta] = db.run:
    for
      up <-
        sql"update pics set access = $access where `key` = $key and user = (select id from users where username = $user)".update.run
      row <- metaQuery(key)
    yield
      log.info(s"User $user changed access of $key to $access.")
      row

  def contains(key: Key): F[Boolean] = db.run:
    sql"select exists(select `key` from pics where `key` = $key)".query[Boolean].unique

  def putMetaIfNotExists(meta: KeyMeta): F[Int] = db.run:
    val q =
      sql"select exists(select `key` from pics where `key` = ${meta.key})".query[Boolean].unique
    q.flatMap: exists =>
      if exists then pure(0)
      else
        for
          userId <- fetchOrCreateUser(meta.owner)
          rows <-
            sql"insert into pics(`key`, user, access, added) values(${meta.key}, $userId, ${meta.access}, ${meta.added})".update.run
        yield rows

  def userByToken(token: AccessToken): F[Option[Username]] = db.run:
    sql"""select u.username from users u, tokens t where t.user = u.id and t.token = $token"""
      .query[Username]
      .option

  private def fetchOrCreateUser(name: PicOwner): ConnectionIO[UserId] = for
    userRow <- sql"select id from users where username = $name".query[UserId].option
    userId <- userRow
      .map(pure)
      .getOrElse(
        sql"insert into users(username) values ($name)".update
          .withUniqueGeneratedKeys[UserId]("id")
      )
  yield userId

  def pure[T](t: T): ConnectionIO[T] = t.pure[ConnectionIO]
