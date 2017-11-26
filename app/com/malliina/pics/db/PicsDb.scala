package com.malliina.pics.db

import java.nio.file.Path

import com.malliina.pics._
import com.malliina.pics.db.Mappings._
import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

object PicsDb {
  def apply(db: PicsDatabase): PicsDb = new PicsDb(db)
}

class PicsDb(db: PicsDatabase) extends MetaSource {
  implicit val ec = db.ec
  val pics = db.picsTable

  def load(from: Int, until: Int): Future[Seq[KeyMeta]] =
    run(pics.sortBy(_.added.desc).drop(from).take(until).result)

  def contains(key: Key): Future[Boolean] =
    run(db.picsTable.filter(_.key === key).exists.result)

  def put(key: Key, file: Path): Future[Unit] = {
    val action = pics.map(_.forInsert) += key
    run(action).map { _ => () }
  }

  def remove(key: Key): Future[Unit] = {
    val action = pics.filter(_.key === key).delete
    run(action).map { _ => () }
  }

  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] =
    db.database.run(a)
}
