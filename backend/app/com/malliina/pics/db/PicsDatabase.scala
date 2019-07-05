package com.malliina.pics.db

import java.nio.file.{Files, Path, Paths}
import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.Instant

import com.malliina.concurrent.Execution
import com.malliina.pics.{Key, KeyMeta, PicOwner}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import org.h2.jdbcx.JdbcConnectionPool
import play.api.{Configuration, Logger, Mode}
import slick.ast.FieldSymbol
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile}
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext

object InstantMySQLProfile extends JdbcProfile with MySQLProfile {
  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes {
    override val instantType = new InstantJdbcType {
      override def sqlTypeName(sym: Option[FieldSymbol]) = "TIMESTAMP(3)"
      override def setValue(v: Instant, p: PreparedStatement, idx: Int): Unit =
        p.setTimestamp(idx, Timestamp.from(v))
      override def getValue(r: ResultSet, idx: Int): Instant =
        Option(r.getTimestamp(idx)).map(_.toInstant).orNull
      override def updateValue(v: Instant, r: ResultSet, idx: Int): Unit =
        r.updateTimestamp(idx, Timestamp.from(v))
      override def valueToSQLLiteral(value: Instant): String = s"'${Timestamp.from(value)}'"
    }
  }
}

object InstantH2Profile extends JdbcProfile with H2Profile {
  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes {
    override val instantType = new InstantJdbcType {
      override def sqlTypeName(sym: Option[FieldSymbol]) = "TIMESTAMP(3)"
      override def setValue(v: Instant, p: PreparedStatement, idx: Int): Unit =
        p.setTimestamp(idx, Timestamp.from(v))
      override def getValue(r: ResultSet, idx: Int): Instant =
        Option(r.getTimestamp(idx)).map(_.toInstant).orNull
      override def updateValue(v: Instant, r: ResultSet, idx: Int): Unit =
        r.updateTimestamp(idx, Timestamp.from(v))
      override def valueToSQLLiteral(value: Instant): String = s"'${Timestamp.from(value)}'"
    }
  }
}

object PicsDatabase {
  private val log = Logger(getClass)
  val H2UrlSettings = "h2.url.settings"
  val TimestampSqlType = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val tmpDir = Paths.get(sys.props("java.io.tmpdir"))

  def forMode(mode: Mode, conf: Configuration) =
    if (mode == Mode.Prod) prod(conf)
    else if (mode == Mode.Dev) dev(conf)
    else inMemory()

  def apply(ds: DataSource, profile: JdbcProfile) =
    new PicsDatabase(ds, profile, Execution.cached)

  def dev(conf: Configuration): PicsDatabase =
    Conf.fromConf(conf).map(mysql).getOrElse(defaultFile())

  def defaultFile() = {
    val path = sys.props.get("pics.home")
      .map(h => Paths.get(h))
      .getOrElse(tmpDir)
      .resolve("picsdb")
    file(path)
  }

  def prod(conf: Configuration) =
    mysql(Conf.fromConf(conf).fold(err => throw new Exception(err), identity))

  /**
    * @param path path to database file
    * @return a file-based database stored at `path`
    */
  def file(path: Path) = {
    Option(path.getParent).foreach(p => Files.createDirectories(p))
    h2(path.toString)
  }

  def h2(memOrFile: String): PicsDatabase = {
    val databaseUrlSettings = sys.props.get(H2UrlSettings)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(ss => s";$ss")
      .getOrElse("")
    val url = s"jdbc:h2:$memOrFile;DB_CLOSE_DELAY=-1$databaseUrlSettings"
    log info s"Connecting to '$url'..."
    apply(JdbcConnectionPool.create(url, "", ""), InstantH2Profile)
  }

  // To keep the content of an in-memory database as long as the virtual machine is alive, use
  // jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1
  def inMemory() = h2("mem:test")

  def mysqlFromEnvOrFail() = mysql(Conf.fromEnvOrFail())

  def mysql(conf: Conf): PicsDatabase = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName(MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    log info s"Connecting to '${conf.url}'..."
    apply(new HikariDataSource(hikari), InstantMySQLProfile)
  }

  def executor(threads: Int) = AsyncExecutor(
    name = "AsyncExecutor.pics",
    minThreads = threads,
    maxThreads = threads,
    queueSize = 1000,
    maxConnections = threads
  )
}

class PicsDatabase(ds: DataSource, p: JdbcProfile, val ec: ExecutionContext)
  extends DatabaseLike(p) {
  val mappings = Mappings(p)
  val api = profile.api

  import profile.api._
  import mappings._

  val picsTable = TableQuery[PicsTable]

  val dbThreads = 20

  override val database = Database.forDataSource(
    ds,
    maxConnections = Option(dbThreads),
    executor = PicsDatabase.executor(dbThreads)
  )

  override def tableQueries = Seq(picsTable)

  class PicsTable(tag: Tag) extends Table[KeyMeta](tag, "pics") {
    def key = column[Key]("key", O.PrimaryKey, O.Length(128))

    def owner = column[PicOwner]("owner", O.Length(128))

    def added = column[Instant]("added", O.SqlType(PicsDatabase.TimestampSqlType))

    def forInsert = (key, owner)

    def * = (key, owner, added) <> ((KeyMeta.apply _).tupled, KeyMeta.unapply)
  }

}
