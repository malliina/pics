package tests

import cats.effect.IO
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
import org.slf4j.LoggerFactory
import org.testcontainers.utility.DockerImageName
import tests.MUnitDatabaseSuite.log
import org.http4s.server.Server
import org.http4s.client.Client
import org.http4s.Uri
import com.malliina.http.FullUrl

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Promise
import scala.util.Try

object MUnitDatabaseSuite:
  private val log = LoggerFactory.getLogger(getClass)

trait MUnitDatabaseSuite:
  self: munit.Suite =>
  val db: Fixture[DatabaseConf] = new Fixture[DatabaseConf]("database"):
    var container: Option[MySQLContainer] = None
    var conf: Option[DatabaseConf] = None
    def apply() = conf.get

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

    def readTestConf = PicsConf.picsConf.read[DatabaseConf]("testdb")

  override def munitFixtures: Seq[Fixture[?]] = Seq(db)

case class ServerTools(server: Server):
  def port = server.address.getPort
  def baseHttpUrl = FullUrl("http", s"localhost:$port", "")
  def baseWsUrl = FullUrl("ws", s"localhost:$port", "")

trait ServerSuite extends MUnitDatabaseSuite with ClientSuite:
  self: FunSuite =>
  val server: Fixture[ServerTools] = new Fixture[ServerTools]("server"):
    private var tools: Option[ServerTools] = None
    val finalizer = new AtomicReference[IO[Unit]](IO.pure(()))
    override def apply(): ServerTools = tools.get
    override def beforeAll(): Unit =
      val testServer = PicsServer.server(
        PicsConf.unsafeLoadWith(PicsConf.picsConf, db()),
        IO.pure(MultiSizeHandlerIO.empty()),
        port = port"12345"
      )
      val (instance, closable) = testServer.map(s => ServerTools(s)).allocated.unsafeRunSync()
      tools = Option(instance)
      finalizer.set(closable)

    override def afterAll(): Unit =
      finalizer.get().unsafeRunSync()

  override def munitFixtures: Seq[Fixture[?]] = Seq(db, server, client)

trait ClientSuite:
  self: FunSuite =>
  implicit val ioRuntime: IORuntime = IORuntime.global

  val client: Fixture[HttpClientIO] = new Fixture[HttpClientIO]("client"):
    var c: Option[HttpClientIO] = None
    def apply(): HttpClientIO = c.get
    override def beforeAll(): Unit =
      c = Option(HttpClientIO())
    override def afterAll(): Unit =
      c.foreach(_.close())

  override def munitFixtures: Seq[Fixture[?]] = Seq(client)

trait DoobieSuite extends MUnitDatabaseSuite:
  self: munit.Suite =>
  val doobie: Fixture[DatabaseRunner[IO]] = new Fixture[DatabaseRunner[IO]]("doobie"):
    var database: Option[DatabaseRunner[IO]] = None
    def apply(): DatabaseRunner[IO] = database.get
    override def beforeAll(): Unit =
      database = Option(DoobieDatabase.withMigrations(db()))
    override def afterAll(): Unit =
      database.foreach(_.close())

  override def munitFixtures: Seq[Fixture[?]] = Seq(db, doobie)

object TestConf:
  def apply(container: MySQLContainer) = DatabaseConf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password
  )
