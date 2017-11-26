package com.malliina.pics.db

import java.nio.file.{Files, Path, Paths}
import java.time.Instant

import com.malliina.concurrent.ExecutionContexts
import com.malliina.file.FileUtilities
import com.malliina.pics.db.Mappings.{instantMapping, keyMapping}
import com.malliina.pics.db.PicsDatabase.log
import com.malliina.pics.{Key, KeyMeta}
import org.h2.jdbcx.JdbcConnectionPool
import play.api.Logger
import slick.jdbc.H2Profile.api._

import scala.concurrent.ExecutionContext

object PicsDatabase {
  private val log = Logger(getClass)
  val H2UrlSettings = "h2.url.settings"
  val TimestampSqlType = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"

  def apply(conn: String) = new PicsDatabase(conn, ExecutionContexts.cached)

  def default() = {
    val path = sys.props.get("pics.home")
      .map(h => Paths.get(h))
      .getOrElse(FileUtilities.tempDir)
      .resolve("picsdb")
    file(path)
  }

  /**
    * @param path path to database file
    * @return a file-based database stored at `path`
    */
  def file(path: Path) = {
    Option(path.getParent).foreach(p => Files.createDirectories(p))
    apply(path.toString)
  }

  // To keep the content of an in-memory database as long as the virtual machine is alive, use
  // jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1
  def test() = apply("mem:test")
}

class PicsDatabase(conn: String, val ec: ExecutionContext) extends DatabaseLike {
  val picsTable = TableQuery[PicsTable]
  val databaseUrlSettings = sys.props.get(PicsDatabase.H2UrlSettings)
    .map(_.trim)
    .filter(_.nonEmpty)
    .map(ss => s";$ss")
    .getOrElse("")
  val url = s"jdbc:h2:$conn;DB_CLOSE_DELAY=-1$databaseUrlSettings"
  log info s"Connecting to: $url"
  val pool = JdbcConnectionPool.create(url, "", "")
  override val database = Database.forDataSource(pool, None)

  override def tableQueries = Seq(picsTable)
}

class PicsTable(tag: Tag) extends Table[KeyMeta](tag, "pics") {
  def key = column[Key]("key", O.Unique)

  def added = column[Instant]("added", O.SqlType(PicsDatabase.TimestampSqlType))

  def forInsert = key

  def * = (key, added) <> ((KeyMeta.apply _).tupled, KeyMeta.unapply)
}
