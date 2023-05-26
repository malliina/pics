package com.malliina.pics.db

import cats.effect.{Async, Sync}
import cats.effect.kernel.Resource
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

  def default[F[_]: Async](conf: DatabaseConf) = migratedResource[F](conf).map { tx =>
    DoobieDatabase(tx)
  }

  def migratedResource[F[_]: Async](
    conf: DatabaseConf
  ): Resource[F, doobie.DataSourceTransactor[F]] =
    val maybeMigration: F[Unit] =
      if conf.migrateOnStart then Async[F].delay(migrate(conf)) else Async[F].unit
    Resource.eval(maybeMigration).flatMap { _ => resource(dataSource(conf)) }

  private def migrate(conf: DatabaseConf): MigrateResult =
    val flyway = Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()

  private def dataSource(conf: DatabaseConf): HikariDataSource =
    val hikari = new HikariConfig()
    hikari.setDriverClassName(DatabaseConf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    hikari.setMaxLifetime(60.seconds.toMillis)
    hikari.setMaximumPoolSize(conf.maxPoolSize)
    log.info(s"Connecting to '${conf.url}' with pool size ${conf.maxPoolSize} as ${conf.user}...")
    new HikariDataSource(hikari)

  private def resource[F[_]: Async](ds: HikariDataSource): Resource[F, DataSourceTransactor[F]] =
    for ec <- ExecutionContexts.fixedThreadPool[F](16) // our connect EC
    yield Transactor.fromDataSource[F](ds, ec)

class DoobieDatabase[F[_]: Sync](tx: DataSourceTransactor[F]) extends DatabaseRunner[F]:
  implicit val logHandler: LogHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      log.info(s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms.")
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }

  def run[T](io: ConnectionIO[T]): F[T] = io.transact(tx)

trait DatabaseRunner[F[_]]:
  def run[T](io: ConnectionIO[T]): F[T]
