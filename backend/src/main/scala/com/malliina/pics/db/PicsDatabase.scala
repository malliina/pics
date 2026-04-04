package com.malliina.pics.db

import cats.implicits.*
import com.malliina.database.DoobieDatabase
import com.malliina.pics.auth.{Cognito, SocialEmail, SpecialUser, UserPayload, UserSubject}
import com.malliina.pics.db.PicsDatabase.log
import com.malliina.pics.{Access, AlreadyExists, FlatMeta, Key, KeyMeta, KeyNotFound, Language, MetaSourceT, PicUsername, Role, UserDatabase}
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, NonNeg, UserId}
import doobie.ConnectionIO
import doobie.implicits.*
import doobie.util.fragment.Fragment

object PicsDatabase:
  private val log = AppLogger(getClass)

class PicsDatabase[F[_]](db: DoobieDatabase[F]) extends MetaSourceT[F] with UserDatabase[F]:
  override def meta(key: Key, user: UserPayload): F[KeyMeta] = db.run:
    keyQuery(key).option.flatMap: metaOpt =>
      metaOpt
        .map: meta =>
          if meta.access == Access.Public then pure(meta)
          else
            existingUser(user).flatMap: u =>
              ownsKey(key, u).flatMap: owns =>
                if owns then pure(meta)
                else
                  log.warn(
                    s"User '$user' not authorized to view ${meta.access} '$key' owned by '${meta.username}'."
                  )
                  fail(KeyNotFound(key))
        .getOrElse:
          fail(KeyNotFound(key))

  def load(offset: NonNeg, limit: NonNeg, user: UserPayload): F[List[KeyMeta]] = db.run:
    userIO(user).flatMap: uOpt =>
      uOpt
        .map: row =>
          val frag = fr"""and u.id = ${row.id}
                          order by p.added desc limit $limit offset $offset
                         """
          metaQuery(frag).to[List]
        .getOrElse:
          pure(Nil)

  def saveMeta(key: Key, owner: UserPayload): F[KeyMeta] = db.run:
    val access = if owner == PicUsername.anon then Access.Public else Access.Private
    for
      user <- fetchOrCreateUser(owner)
      _ <- sql"insert into pics(`key`, user, access) values ($key, ${user.id}, $access)".update.run
      row <- metaQuery(fr"and p.`key` = $key and u.id = ${user.id}").unique
    yield
      log.info(s"Inserted '$key' by '${user.username}'.")
      row

  def remove(key: Key, user: UserPayload): F[Boolean] = db.run:
    existingUser(user).flatMap: row =>
      sql"delete from pics where `key` = $key and user = (select id from users where id = ${row.id})".update.run
        .map: deleted =>
          val wasDeleted = deleted > 0
          if wasDeleted then log.info(s"Deleted '$key' by '${user.username}'.")
          else log.warn(s"Tried to remove '$key' by '${user.username}' but found no matching rows.")
          wasDeleted

  override def modify(key: Key, user: UserPayload, access: Access): F[KeyMeta] = db.run:
    existingUser(user).flatMap: row =>
      for
        ownsPic <- ownsKey(key, row)
        _ <- if !ownsPic then fail(KeyNotFound(key)) else pure(())
        _ <-
          sql"update pics set access = $access where `key` = $key and user = ${row.id}".update.run
        row <- metaRow(key)
      yield
        log.info(s"User $user changed access of $key to $access.")
        row

  private def ownsKey(key: Key, user: UserRow): ConnectionIO[Boolean] =
    sql"""select exists(select p.`key`
                        from pics p, users u
                        where p.user = u.id and p.`key` = $key and u.id = ${user.id})"""
      .query[Boolean]
      .unique

  def contains(key: Key): F[Boolean] = db.run:
    existsQuery(key)

  private def existsQuery(key: Key) =
    sql"select exists(select `key` from pics where `key` = $key)".query[Boolean].unique

  def putMetaIfNotExists(meta: FlatMeta, user: UserPayload): F[Int] = db.run:
    val q = existsQuery(meta.key)
    q.flatMap: exists =>
      if exists then pure(0)
      else
        for
          user <- fetchOrCreateUser(user)
          rows <-
            sql"""insert into pics(`key`, user, access, added)
                  values(${meta.key}, ${user.id}, ${Access.Private}, ${meta.lastModified})""".update.run
        yield rows

  def userByToken(token: AccessToken): F[Option[UserRow]] = db.run:
    sql"""$selectUsers, tokens t
          where t.user = u.id and t.token = $token"""
      .query[UserRow]
      .option

  override def loadUser(user: UserPayload): F[Option[UserRow]] = db.run:
    userIO(user)

  private def existingUser(user: UserPayload) = userQuery(user.subject).unique
  private def userIO(user: UserPayload) = userQuery(user.subject).option

  private def userQuery(subject: UserSubject) =
    val q = subject match
      case SocialEmail(email) => sql"""$selectUsers where u.email = $email"""
      case Cognito(id)        => sql"""$selectUsers where u.cognito_sub = $id"""
      case SpecialUser(name)  => sql"""$selectUsers where u.username = $name"""
    q.query[UserRow]

  private def userById(id: UserId) =
    sql"""$selectUsers where u.id = $id""".query[UserRow]

  private def selectUsers =
    sql"""select u.id, u.username, u.email, u.cognito_sub, u.role, u.language, u.added
          from users u"""

  private def metaRow(key: Key) = keyQuery(key).unique

  private def keyQuery(key: Key) = metaQuery(fr"and p.`key` = $key")

  private def metaQuery(fragment: Fragment) =
    sql"""select p.`key`, u.username, u.email, u.cognito_sub, p.access, p.added
          from pics p, users u
          where p.user = u.id $fragment""".query[KeyMeta]

  private def fetchOrCreateUser(user: UserPayload): ConnectionIO[UserRow] =
    for
      userOpt <- userIO(user)
      user <- userOpt
        .map(pure)
        .getOrElse(insertUserIO(user))
    yield user

  private def insertUserIO(user: UserPayload): ConnectionIO[UserRow] =
    for
      exists <-
        sql"""select exists(select username from users u where u.username = ${user.username})"""
          .query[Boolean]
          .unique
      _ <- if exists then fail(AlreadyExists(user.username)) else pure(())
      id <- sql"""insert into users(username, email, cognito_sub, role, language)
                  values (${user.username}, ${user.email}, ${user.cognito}, ${Role.default}, ${Language.default})""".update
        .withUniqueGeneratedKeys[UserId]("id")
      row <- userById(id).unique
    yield row

  private def pure[T](t: T): ConnectionIO[T] = t.pure[ConnectionIO]

  private def fail[A](e: Exception): ConnectionIO[A] = e.raiseError[ConnectionIO, A]
