package com.malliina.pics.db

import com.malliina.pics.db.DoobiePicsDatabase.log
import com.malliina.pics.{Key, KeyMeta, MetaSource, PicOwner}
import doobie.implicits._
import play.api.Logger

import scala.concurrent.Future

object DoobiePicsDatabase {
  private val log = Logger(getClass)

  def apply(db: DoobieDatabase): DoobiePicsDatabase = new DoobiePicsDatabase(db)
}

class DoobiePicsDatabase(db: DoobieDatabase) extends MetaSource {
  def load(from: Int, until: Int, user: PicOwner): Future[Seq[KeyMeta]] = db.run {
    val limit = until - from
    sql"""select `key`, owner, added
         from pics 
         where owner = $user 
         order by added desc limit $limit offset $from"""
      .query[KeyMeta]
      .to[List]
  }

  def saveMeta(key: Key, owner: PicOwner): Future[KeyMeta] = db.run {
    val insertion = sql"insert into pics(`key`, owner) values ($key, $owner)"
    for {
      _ <- sql"insert into pics(`key`, owner) values ($key, $owner)".update.run
      row <- sql"select `key`, owner, added from pics where `key` = $key and owner = $owner"
        .query[KeyMeta]
        .unique
    } yield {
      log.info(s"Inserted '$key' by '$owner'.")
      row
    }
  }

  def remove(key: Key, user: PicOwner): Future[Boolean] = db.run {
    sql"delete from pics where owner = $user and `key` = $key".update.run.map { deleted =>
      val wasDeleted = deleted > 0
      if (wasDeleted) {
        log.info(s"Deleted '$key' by '$user'.")
      } else {
        log.warn(s"Tried to remove '$key' by '$user' but found no matching rows.")
      }
      wasDeleted
    }
  }

  def contains(key: Key): Future[Boolean] = db.run {
    sql"select exists(select `key` from pics where `key` = $key)".query[Boolean].unique
  }

  def putMetaIfNotExists(meta: KeyMeta): Future[Int] = db.run {
    val q =
      sql"select exists(select `key` from pics where `key` = ${meta.key})".query[Boolean].unique
    q.flatMap { exists =>
      if (exists) {
        AsyncConnectionIO.pure(0)
      } else {
        sql"insert into pics(`key`, owner, added) values(${meta.key}, ${meta.owner}, ${meta.added})".update.run
      }
    }
  }
}
