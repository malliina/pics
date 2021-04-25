package com.malliina.pics.db

import com.malliina.pics.db.DoobiePicsDatabase.log
import com.malliina.pics.{Key, KeyMeta, MetaSourceT, PicOwner}
import com.malliina.util.AppLogger
import com.malliina.values.UserId
import doobie.ConnectionIO
import doobie.implicits._

object DoobiePicsDatabase {
  private val log = AppLogger(getClass)

  def apply[F[_]](db: DatabaseRunner[F]): DoobiePicsDatabase[F] = new DoobiePicsDatabase(db)
}

class DoobiePicsDatabase[F[_]](db: DatabaseRunner[F]) extends MetaSourceT[F] {
  def load(from: Int, until: Int, user: PicOwner): F[List[KeyMeta]] = db.run {
    val limit = until - from
    sql"""select p.`key`, u.username, p.added
          from pics p, users u
          where p.user = u.id and u.username = $user 
          order by p.added desc limit $limit offset $from"""
      .query[KeyMeta]
      .to[List]
  }

  def saveMeta(key: Key, owner: PicOwner): F[KeyMeta] = db.run {
    for {
      userId <- fetchOrCreateUser(owner)
      _ <- sql"insert into pics(`key`, user) values ($key, $userId)".update.run
      row <-
        sql"""select p.`key`, u.username, p.added 
              from pics p, users u 
              where p.user = u.id and p.`key` = $key and u.username = $owner"""
          .query[KeyMeta]
          .unique
    } yield {
      log.info(s"Inserted '$key' by '$owner'.")
      row
    }
  }

  def remove(key: Key, user: PicOwner): F[Boolean] = db.run {
    sql"delete from pics where `key` = $key and user = (select id from users where username = $user)".update.run
      .map { deleted =>
        val wasDeleted = deleted > 0
        if (wasDeleted) {
          log.info(s"Deleted '$key' by '$user'.")
        } else {
          log.warn(s"Tried to remove '$key' by '$user' but found no matching rows.")
        }
        wasDeleted
      }
  }

  def contains(key: Key): F[Boolean] = db.run {
    sql"select exists(select `key` from pics where `key` = $key)".query[Boolean].unique
  }

  def putMetaIfNotExists(meta: KeyMeta): F[Int] = db.run {
    val q =
      sql"select exists(select `key` from pics where `key` = ${meta.key})".query[Boolean].unique
    q.flatMap { exists =>
      if (exists) {
        pure(0)
      } else {
        for {
          userId <- fetchOrCreateUser(meta.owner)
          rows <-
            sql"insert into pics(`key`, user, added) values(${meta.key}, $userId, ${meta.added})".update.run
        } yield rows
      }
    }
  }

  private def fetchOrCreateUser(name: PicOwner): ConnectionIO[UserId] = for {
    userRow <- sql"select id from users where username = $name".query[UserId].option
    userId <- userRow
      .map(pure)
      .getOrElse(
        sql"insert into users(username) values ($name)".update
          .withUniqueGeneratedKeys[UserId]("id")
      )
  } yield userId

  def pure[T](t: T) = AsyncConnectionIO.pure(t)
}
