package tests

import cats.effect.IO
import cats.effect.IO.asyncForIO
import cats.effect.kernel.Resource
import cats.syntax.flatMap.*
import com.comcast.ip4s.port
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.config.ConfigError
import com.malliina.database.{Conf, DoobieDatabase}
import com.malliina.http.FullUrl
import com.malliina.http.io.{HttpClientF2, HttpClientIO}
import com.malliina.pics.*
import com.malliina.pics.http4s.PicsServer
import org.http4s.server.Server
import org.slf4j.LoggerFactory
import org.testcontainers.utility.DockerImageName
import tests.MUnitDatabaseSuite.log

object MUnitDatabaseSuite:
  private val log = LoggerFactory.getLogger(getClass)

trait MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val db: Fixture[Conf] = new Fixture[Conf]("database"):
    var container: Option[MySQLContainer] = None
    var conf: Option[Conf] = None
    def apply(): Conf = conf.get

    override def beforeAll(): Unit =
      val testDb = readTestConf.recover { err =>
        log.info(s"No local test database configured, falling back to Docker. $err")
        val c = MySQLContainer(mysqlImageVersion = DockerImageName.parse("mysql:8.0.33"))
        c.start()
        container = Option(c)
        TestConf(c)
      }
      conf = Option(testDb)
    override def afterAll(): Unit =
      container.foreach(_.stop())

    def readTestConf: Either[ConfigError, Conf] =
      PicsConf.picsConf.parse[Conf]("testdb")

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
        PicsConf.unsafeLoadWith(PicsConf.picsConf, Right(db())),
        Resource.eval(IO(MultiSizeHandler.empty())),
        port = port"12345"
      )
      .map(s => ServerTools(s))
  )

  override def munitFixtures: Seq[Fixture[?]] = Seq(db, server, client)

trait ClientSuite:
  self: munit.CatsEffectSuite =>
  val client: Fixture[HttpClientF2[IO]] = ResourceSuiteLocalFixture("client", HttpClientIO.resource)

  override def munitFixtures: Seq[Fixture[?]] = Seq(client)

trait DoobieSuite extends MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val doobie: Fixture[DoobieDatabase[IO]] =
    ResourceSuiteLocalFixture(
      "doobie",
      Resource.eval(IO.delay(db())).flatMap(d => DoobieDatabase.init(d))
    )

  override def munitFixtures: Seq[Fixture[?]] = Seq(db, doobie)

object TestConf:
  def apply(container: MySQLContainer): Conf = Conf(
    container.jdbcUrl,
    container.username,
    container.password,
    Conf.MySQLDriver,
    2,
    true
  )
