package tests

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import cats.syntax.flatMap.*
import com.comcast.ip4s.port
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.pics.*
import com.malliina.pics.PicsConf.ConfigOps
import com.malliina.pics.app.LocalConf
import com.malliina.pics.db.{DatabaseConf, DatabaseRunner, DoobieDatabase}
import com.malliina.pics.http4s.PicsServer
import com.malliina.pics.http4s.PicsServer.AppService
import munit.FunSuite
import com.malliina.http.io.HttpClientIO
import com.malliina.values.ErrorMessage
import org.slf4j.LoggerFactory
import org.testcontainers.utility.DockerImageName
import tests.MUnitDatabaseSuite.log
import org.http4s.server.Server
import org.http4s.Uri
import com.malliina.http.FullUrl

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Promise
import scala.util.Try

object MUnitDatabaseSuite:
  private val log = LoggerFactory.getLogger(getClass)

trait MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val db: Fixture[DatabaseConf] = new Fixture[DatabaseConf]("database"):
    var container: Option[MySQLContainer] = None
    var conf: Option[DatabaseConf] = None
    def apply(): DatabaseConf = conf.get

    override def beforeAll(): Unit =
      val testDb = readTestConf.recover { err =>
        log.info(s"No local test database configured, falling back to Docker. $err")
        val c = MySQLContainer(mysqlImageVersion = DockerImageName.parse("mysql:5.7.29"))
        c.start()
        container = Option(c)
        TestConf(c)
      }
      conf = Option(testDb)
    override def afterAll(): Unit =
      container.foreach(_.stop())

    def readTestConf: Either[ErrorMessage, DatabaseConf] =
      PicsConf.picsConf.read[DatabaseConf]("testdb")

  override def munitFixtures: Seq[Fixture[?]] = Seq(db)

case class ServerTools(server: Server):
  def port = server.address.getPort
  def baseHttpUrl = FullUrl("http", s"localhost:$port", "")
  def baseWsUrl = FullUrl("ws", s"localhost:$port", "")

trait ServerSuite extends MUnitDatabaseSuite with ClientSuite:
  self: munit.CatsEffectSuite =>
  val server: Fixture[ServerTools] = ResourceSuiteLocalFixture(
    "server",
    PicsServer
      .server(
        PicsConf.unsafeLoadWith(PicsConf.picsConf, db()),
        Resource.eval(IO(MultiSizeHandler.empty())),
        port = port"12345"
      )
      .map(s => ServerTools(s))
  )

  override def munitFixtures: Seq[Fixture[?]] = Seq(db, server, client)

trait ClientSuite:
  self: munit.CatsEffectSuite =>
  val client: Fixture[HttpClientIO] = ResourceSuiteLocalFixture("client", HttpClientIO.resource)

  override def munitFixtures: Seq[Fixture[?]] = Seq(client)

trait DoobieSuite extends MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val doobie: Fixture[DatabaseRunner[IO]] =
    ResourceSuiteLocalFixture("doobie", DoobieDatabase.withMigrations(db()))

  override def munitFixtures: Seq[Fixture[?]] = Seq(db, doobie)

object TestConf:
  def apply(container: MySQLContainer): DatabaseConf = DatabaseConf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password
  )
