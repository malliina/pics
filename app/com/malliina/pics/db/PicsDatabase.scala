package com.malliina.pics.db

import java.nio.file.{Files, Path, Paths}
import java.time.Instant

import com.malliina.concurrent.ExecutionContexts
import com.malliina.file.FileUtilities
import com.malliina.pics.{Key, KeyMeta, PicOwner}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import org.h2.jdbcx.JdbcConnectionPool
import play.api.{Logger, Mode}
import slick.jdbc.{H2Profile, JdbcProfile, MySQLProfile}

import scala.concurrent.ExecutionContext

object PicsDatabase {
  private val log = Logger(getClass)
  val H2UrlSettings = "h2.url.settings"
  val TimestampSqlType = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"

  def forMode(mode: Mode) = {
    if (mode == Mode.Prod) prod()
    else if (mode == Mode.Dev) dev()
    else inMemory()
  }

  def apply(ds: DataSource, profile: JdbcProfile) = new PicsDatabase(ds, profile, ExecutionContexts.cached)

  def dev() = Conf.fromEnv().map(maria).getOrElse(defaultFile())

  def defaultFile() = {
    val path = sys.props.get("pics.home")
      .map(h => Paths.get(h))
      .getOrElse(FileUtilities.tempDir)
      .resolve("picsdb")
    file(path)
  }
  
  def prod() = PicsDatabase.mariaFromEnvOrFail()

  /**
    * @param path path to database file
    * @return a file-based database stored at `path`
    */
  def file(path: Path) = {
    Option(path.getParent).foreach(p => Files.createDirectories(p))
    h2(path.toString)
  }

  def h2(memOrFile: String): PicsDatabase = {
    val databaseUrlSettings = sys.props.get(PicsDatabase.H2UrlSettings)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(ss => s";$ss")
      .getOrElse("")
    val url = s"jdbc:h2:$memOrFile;DB_CLOSE_DELAY=-1$databaseUrlSettings"
    log info s"Connecting to '$url'..."
    apply(JdbcConnectionPool.create(url, "", ""), H2Profile)
  }

  // To keep the content of an in-memory database as long as the virtual machine is alive, use
  // jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1
  def inMemory() = h2("mem:test")

  def mariaFromEnvOrFail() = maria(Conf.fromEnvOrFail())

  def maria(conf: Conf): PicsDatabase = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName("org.mariadb.jdbc.Driver")
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    log info s"Connecting to '${conf.url}'..."
    apply(new HikariDataSource(hikari), MySQLProfile)
  }
}

class PicsDatabase(ds: DataSource, p: JdbcProfile, val ec: ExecutionContext)
  extends DatabaseLike(p) {
  val mappings = Mappings(p)
  val api = profile.api

  import profile.api._
  import mappings._

  val picsTable = TableQuery[PicsTable]

  override val database = Database.forDataSource(ds, None)

  override def tableQueries = Seq(picsTable)

  class PicsTable(tag: Tag) extends Table[KeyMeta](tag, "pics") {
    def key = column[Key]("key", O.PrimaryKey, O.Length(128))

    def owner = column[PicOwner]("owner", O.Length(128))

    def added = column[Instant]("added", O.SqlType(PicsDatabase.TimestampSqlType))

    def forInsert = (key, owner)

    def * = (key, owner, added) <> ((KeyMeta.apply _).tupled, KeyMeta.unapply)
  }

}
