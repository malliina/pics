package tests

import cats.effect
import cats.effect.IO
import cats.effect.IO.asyncForIO
import cats.effect.kernel.Resource
import cats.syntax.flatMap.*
import ch.qos.logback.classic.Level
import com.comcast.ip4s.port
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.config.ConfigError
import com.malliina.database.{Conf, DoobieDatabase}
import com.malliina.http.FullUrl
import com.malliina.http.io.{HttpClientF2, HttpClientIO}
import com.malliina.logback.LogbackUtils
import com.malliina.pics.*
import com.malliina.pics.http4s.PicsApp
import com.malliina.util.AppLogger
import com.malliina.values.Password
import org.http4s.server.Server
import org.testcontainers.utility.DockerImageName
import tests.MUnitDatabaseSuite.log

object MUnitDatabaseSuite:
  private val log = AppLogger(getClass)

trait MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val db: Fixture[Conf] = new Fixture[Conf]("database"):
    var container: Option[MySQLContainer] = None
    var conf: Option[Conf] = None
    def apply(): Conf = conf.get

    override def beforeAll(): Unit =
      val testDb = readTestConf.recover: err =>
        log.info(s"No local test database configured, falling back to Docker. $err")
        val c = MySQLContainer(mysqlImageVersion = DockerImageName.parse("mysql:8.0.33"))
        c.start()
        container = Option(c)
        TestConf(c)
      conf = Option(testDb)
    override def afterAll(): Unit =
      container.foreach(_.stop())

    def readTestConf: Either[ConfigError, Conf] =
      PicsConf.picsConf.parse[Password]("testdb.pass").map(testDatabaseConf)

    private def testDatabaseConf(password: Password) = Conf(
      "jdbc:mysql://localhost:3306/testpics",
      "testpics",
      password.pass,
      Conf.MySQLDriver,
      maxPoolSize = 2,
      autoMigrate = true
    )

  override def munitFixtures: Seq[Fixture[?]] = Seq(db)

case class ServerTools(server: Server):
  def port = server.address.getPort
  def baseHttpUrl = FullUrl("http", s"localhost:$port", "")
  def baseWsUrl = FullUrl("ws", s"localhost:$port", "")

trait ServerSuite extends MUnitDatabaseSuite with ClientSuite:
  self: munit.CatsEffectSuite =>
  object TestServer extends PicsApp:
    LogbackUtils.init(rootLevel = Level.OFF)

  val server: Fixture[ServerTools] = ResourceSuiteLocalFixture(
    "server",
    for
      conf <- Resource
        .eval(
          IO.delay(PicsConf.loadWith(Right(db())))
            .flatMap(_.fold(err => IO.raiseError(err), ok => IO.pure(ok)))
        )
        .map(_.copy(isTest = true))
      s <- TestServer
        .server(
          conf,
          Resource.eval(MultiSizeHandler.empty()),
          port = port"12345"
        )
    yield ServerTools(s)
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
      Resource
        .eval(IO.delay(db()))
        .flatMap: d =>
          DoobieDatabase.init(d)
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
