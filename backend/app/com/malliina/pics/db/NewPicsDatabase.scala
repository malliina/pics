package com.malliina.pics.db

import java.io.Closeable
import java.time.Instant
import java.util.Date

import com.github.jasync.sql.db.ConnectionPoolConfigurationBuilder
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.malliina.pics.db.NewPicsDatabase.{DatabaseContext, fail, log}
import com.malliina.pics.{Key, KeyMeta, MetaSource, PicOwner}
import io.getquill._
import org.flywaydb.core.Flyway
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewPicsDatabase {
  private val log = Logger(getClass)

  def apply(conf: Conf, ec: ExecutionContext): NewPicsDatabase = {
    val pool = MySQLConnectionBuilder.createConnectionPool(
      conf.url,
      (c: ConnectionPoolConfigurationBuilder) => {
        c.setUsername(conf.user)
        c.setPassword(conf.pass)
        kotlin.Unit.INSTANCE
      }
    )
    val ctx: MysqlJAsyncContext[CompositeNamingStrategy2[SnakeCase.type, MysqlEscape.type]] =
      new MysqlJAsyncContext(NamingStrategy(SnakeCase, MysqlEscape), pool)
    new NewPicsDatabase(ctx)(ec)
  }

  def withMigrations(conf: Conf, ec: ExecutionContext) = {
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
    apply(conf, ec)
  }

  def fail(message: String): Nothing = throw new Exception(message)

  type DatabaseContext =
    MysqlJAsyncContext[CompositeNamingStrategy2[SnakeCase.type, MysqlEscape.type]]
}

class NewPicsDatabase(val ctx: DatabaseContext)(implicit val ec: ExecutionContext)
  extends MetaSource
  with Closeable {
  import ctx._

  implicit val instantDecoder = MappedEncoding[Date, Instant](d => d.toInstant)
  implicit val instantEncoder = MappedEncoding[Instant, Date](i => Date.from(i))

  val pics = quote(querySchema[KeyMeta]("pics"))

  def load(from: Int, until: Int, user: PicOwner): Future[Seq[KeyMeta]] = {
    val q = quote {
      pics
        .filter(_.owner == lift(user))
        .sortBy(_.added)(Ord.desc)
        .drop(lift(from))
        .take(lift(until))
    }
    run(q)
  }

  def saveMeta(key: Key, owner: PicOwner): Future[KeyMeta] = transactionally {
    val q = quote {
      pics.insert(_.key -> lift(key), _.owner -> lift(owner))
    }
    runIO(q).flatMap { insertion =>
      if (insertion > 0) {
        log.info(s"Inserted '$key' by '$owner'.")
      }
      val added = quote {
        pics.filter(p => p.key == lift(key) && p.owner == lift(owner))
      }
      runIO(added).map { metas =>
        metas.headOption.getOrElse(fail(s"Failed to find inserted picture meta for '$key'."))
      }
    }
  }

  def remove(key: Key, user: PicOwner): Future[Boolean] = {
    val deletedF = run {
      quote {
        pics.filter(pic => pic.owner == lift(user) && pic.key == lift(key)).delete
      }
    }
    deletedF.map { deleted =>
      val wasDeleted = deleted > 0
      if (wasDeleted) {
        log.info(s"Deleted '$key' by '$user'.")
      } else {
        log.warn(s"Tried to remove '$key' by '$user' but found no matching rows.")
      }
      wasDeleted
    }
  }

  def contains(key: Key): Future[Boolean] = {
    val q = quote { pics.filter(_.key == lift(key)).nonEmpty }
    run(q)
  }

  def putMetaIfNotExists(meta: KeyMeta): Future[Int] = {
    val q = quote {
      pics.filter(_.key == lift(meta.key)).nonEmpty
    }
    val a = runIO(q).flatMap { (exists: Boolean) =>
      if (exists) {
        IO.successful(0)
      } else {
        val insertion = quote {
          pics.insert(
            _.key -> lift(meta.key),
            _.owner -> lift(meta.owner),
            _.added -> lift(meta.added)
          )
        }
        runIO(insertion).map(_.toInt)
      }
    }
    transactionally(a)
  }

  def perform[T](io: IO[T, _]) = performIO(io)
  def transactionally[T](io: IO[T, _]) = performIO(io.transactional)

  def close(): Unit = ctx.close()
}
