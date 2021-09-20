package com.malliina.pics.db

import cats.effect.IO.*
import cats.effect.{IO, Resource}
import com.malliina.pics.db.DoobieDatabase.log
import com.malliina.util.AppLogger
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.*
import doobie.implicits.*
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

import scala.concurrent.duration.DurationInt

object DoobieDatabase:
  private val log = AppLogger(getClass)

  def apply(conf: DatabaseConf): DatabaseRunner[IO] =
    apply(dataSource(conf))

  def apply(ds: HikariDataSource): DatabaseRunner[IO] =
    new DoobieDatabaseLegacy(ds)

  def withMigrations(conf: DatabaseConf): DatabaseRunner[IO] =
    migrate(conf)
    apply(conf)

  def migrate(conf: DatabaseConf): MigrateResult =
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()

  def dataSource(conf: DatabaseConf): HikariDataSource =
    val hikari = new HikariConfig()
    hikari.setDriverClassName(DatabaseConf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    hikari.setMaxLifetime(60.seconds.toMillis)
    hikari.setMaximumPoolSize(5)
    log.info(s"Connecting to '${conf.url}'...")
    new HikariDataSource(hikari)

  def migratedResource(conf: DatabaseConf) =
    migrate(conf)
    resource(dataSource(conf))

  def resource(ds: HikariDataSource): Resource[IO, DataSourceTransactor[IO]] =
    for ec <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
    yield Transactor.fromDataSource[IO](ds, ec)

  def apply(tx: DataSourceTransactor[IO]) = new DoobieDatabase(tx)

// Temporary migration tool
class DoobieDatabase(tx: DataSourceTransactor[IO]) extends DatabaseRunner[IO]:
  implicit val logHandler: LogHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      log.info(s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms.")
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }

  def run[T](io: ConnectionIO[T]): IO[T] = io.transact(tx)
  def close(): Unit = ()

trait DatabaseRunner[F[_]]:
  def run[T](io: ConnectionIO[T]): F[T]
  def close(): Unit

class DoobieDatabaseLegacy(ds: HikariDataSource) extends DatabaseRunner[IO]:
  private val tx = DoobieDatabase.resource(ds)

  def run[T](io: ConnectionIO[T]): IO[T] =
    tx.use(r => io.transact(r))

  def close(): Unit = ds.close()
