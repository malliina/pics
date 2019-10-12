package com.malliina.pics.db

import java.io.Closeable
import java.time.Instant
import java.util.Date

import akka.actor.ActorSystem
import com.malliina.pics.db.NewPicsDatabase.{fail, log}
import com.malliina.pics.{Key, KeyMeta, MetaSource, PicOwner}
import com.zaxxer.hikari.HikariDataSource
import io.getquill.{MysqlEscape, MysqlJdbcContext, NamingStrategy, SnakeCase}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewPicsDatabase {
  private val log = Logger(getClass)

  def apply(as: ActorSystem, dbConf: Conf): NewPicsDatabase = {
    val pool = as.dispatchers.lookup("contexts.database")
    apply(PicsDatabase.dataSource(dbConf), pool)
  }

  def apply(ds: HikariDataSource, ec: ExecutionContext): NewPicsDatabase = new NewPicsDatabase(ds)(ec)

  def fail(message: String): Nothing = throw new Exception(message)
}

class NewPicsDatabase(ds: HikariDataSource)(implicit val ec: ExecutionContext) extends MetaSource with Closeable {
  val naming = NamingStrategy(SnakeCase, MysqlEscape)
  lazy val ctx = new MysqlJdbcContext(naming, ds)
  import ctx._

  implicit val instantDecoder = MappedEncoding[Date, Instant](d => d.toInstant)
  implicit val instantEncoder = MappedEncoding[Instant, Date](i => Date.from(i))

  val pics = quote(querySchema[KeyMeta]("pics"))

  def load(from: Int, until: Int, user: PicOwner): Future[Seq[KeyMeta]] = Future {
    val q = quote {
      pics.filter(_.owner == lift(user)).sortBy(_.added)(Ord.desc).drop(lift(from)).take(lift(until))
    }
    run(q)
  }

  def saveMeta(key: Key, owner: PicOwner): Future[KeyMeta] = Future {
    transaction {
      val q = quote {
        pics.insert(_.key -> lift(key), _.owner -> lift(owner))
      }
      val insertion = run(q)
      if (insertion > 0) {
        log.info(s"Inserted '$key' by '$owner'.")
      }
      val added = quote {
        pics.filter(p => p.key == lift(key) && p.owner == lift(owner))
      }
      run(added).headOption.getOrElse(fail(s"Failed to find inserted picture meta for '$key'."))
    }
  }

  def remove(key: Key, user: PicOwner): Future[Boolean] = Future {
    val deleted = run {
      quote {
        pics.filter(pic => pic.owner == lift(user) && pic.key == lift(key)).delete
      }
    }
    val wasDeleted = deleted > 0
    if (wasDeleted) {
      log.info(s"Deleted '$key' by '$user'.")
    } else {
      log.warn(s"Tried to remove '$key' by '$user' but found no matching rows.")
    }
    wasDeleted
  }

  def contains(key: Key): Future[Boolean] = Future {
    val q = quote { pics.filter(_.key == lift(key)).nonEmpty }
    run(q)
  }

  def putMetaIfNotExists(meta: KeyMeta): Future[Int] = Future {
    transaction {
      val q = quote {
        pics.filter(_.key == lift(meta.key)).nonEmpty
      }
      val exists = run(q)
      if (exists) {
        0
      } else {
        val insertion = quote {
          pics.insert(_.key -> lift(meta.key), _.owner -> lift(meta.owner), _.added -> lift(meta.added))
        }
        run(insertion).toInt
      }
    }
  }

  def close(): Unit = ds.close()
}
