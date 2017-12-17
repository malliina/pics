package com.malliina.pics.db

import com.malliina.pics._
import com.malliina.pics.db.Mappings._
import com.malliina.play.models.Username
import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

object PicsSource {
  def apply(db: PicsDatabase): PicsSource = new PicsSource(db)

  def inMemory(): PicsSource = {
    val db = PicsDatabase.inMemory()
    db.init()
    apply(db)
  }
}

class PicsSource(db: PicsDatabase) extends MetaSource {
  implicit val ec = db.ec
  val pics = db.picsTable

  def load(from: Int, until: Int, user: Username): Future[Seq[KeyMeta]] =
    run(pics.filter(_.owner === user).sortBy(_.added.desc).drop(from).take(until).result)

  def contains(key: Key): Future[Boolean] =
    run(pics.filter(_.key === key).exists.result)

  def saveMeta(key: Key, owner: Username): Future[KeyMeta] = {
    val action = for {
      _ <- pics.map(_.forInsert) += (key, owner)
      entry <- pics.filter(p => p.key === key && p.owner === owner).result.headOption
    } yield entry
    run(action).flatMap { maybeRow =>
      maybeRow
        .map(r => fut[KeyMeta](r))
        .getOrElse(Future.failed(new Exception(s"Failed to find inserted picture meta for '$key'.")))
    }
  }

  def putMeta(meta: KeyMeta): Future[Int] = {
    val action = pics.filter(_.key === meta.key).exists.result.flatMap { exists =>
      if (exists) DBIO.successful(0)
      else pics += meta
    }
    run(action.transactionally)
  }

  def remove(key: Key, user: Username): Future[Boolean] = {
    val action = pics.filter(pic => pic.owner === user && pic.key === key).delete
    run(action).map { i => i > 0 }
  }

  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] =
    db.database.run(a)
}
