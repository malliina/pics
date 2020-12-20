package com.malliina.pics.db

import cats.effect.IO._
import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.malliina.pics.db.DoobieDatabase.log
import com.malliina.util.AppLogger
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

import scala.concurrent.duration.DurationInt

object DoobieDatabase {
  private val log = AppLogger(getClass)

  def apply(conf: DatabaseConf, cs: ContextShift[IO]): DatabaseRunner[IO] =
    apply(dataSource(conf), cs)

  def apply(ds: HikariDataSource, cs: ContextShift[IO]): DatabaseRunner[IO] =
    new DoobieDatabaseLegacy(ds, cs)

  def withMigrations(conf: DatabaseConf, cs: ContextShift[IO]): DatabaseRunner[IO] = {
    migrate(conf)
    apply(conf, cs)
  }

  def migrate(conf: DatabaseConf): MigrateResult = {
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
  }

  def dataSource(conf: DatabaseConf): HikariDataSource = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName(DatabaseConf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    hikari.setMaxLifetime(60.seconds.toMillis)
    hikari.setMaximumPoolSize(5)
    log.info(s"Connecting to '${conf.url}'...")
    new HikariDataSource(hikari)
  }

  def migratedResource(conf: DatabaseConf, blocker: Blocker)(implicit cs: ContextShift[IO]) = {
    migrate(conf)
    resource(dataSource(conf), blocker)
  }

  def resource(
    ds: HikariDataSource
  )(implicit cs: ContextShift[IO]): Resource[IO, DataSourceTransactor[IO]] =
    Blocker[IO].flatMap { blocker =>
      resource(ds, blocker)
    }

  def resource(
    ds: HikariDataSource,
    blocker: Blocker
  )(implicit cs: ContextShift[IO]): Resource[IO, DataSourceTransactor[IO]] =
    for {
      ec <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
    } yield Transactor.fromDataSource[IO](ds, ec, blocker)

  def apply(tx: DataSourceTransactor[IO]) = new DoobieDatabase(tx)
}

// Temporary migration tool
class DoobieDatabase(tx: DataSourceTransactor[IO]) extends DatabaseRunner[IO] {
  implicit val logHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      log.info(s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms.")
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }

  def run[T](io: ConnectionIO[T]): IO[T] = io.transact(tx)
  def close(): Unit = ()
}

trait DatabaseRunner[F[_]] {
  def run[T](io: ConnectionIO[T]): F[T]
  def close(): Unit
}

class DoobieDatabaseLegacy(ds: HikariDataSource, cs: ContextShift[IO]) extends DatabaseRunner[IO] {
  implicit val cShift = cs
  private val tx = DoobieDatabase.resource(ds)

  def run[T](io: ConnectionIO[T]): IO[T] =
    tx.use(r => io.transact(r))

  def close(): Unit = ds.close()
}
