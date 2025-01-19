package tests

import cats.data.NonEmptyList
import cats.effect
import cats.effect.IO
import cats.effect.IO.asyncForIO
import cats.effect.kernel.Resource
import cats.syntax.flatMap.*
import ch.qos.logback.classic.Level
import com.comcast.ip4s.port
import com.malliina.config.{ConfigError, ConfigNode, MissingValue}
import com.malliina.database.{Conf, DoobieDatabase}
import com.malliina.http.FullUrl
import com.malliina.http.UrlSyntax.url
import com.malliina.http.io.HttpClientIO
import com.malliina.logback.LogbackUtils
import com.malliina.pics.*
import com.malliina.pics.app.LocalConf
import com.malliina.pics.http4s.AppResources
import com.malliina.values.Password
import munit.AnyFixture
import munit.catseffect.IOFixture
import org.http4s.server.Server

trait MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val testConf: ConfigNode = LocalConf.local("test-pics.conf")

  val db: Fixture[Conf] = new Fixture[Conf]("database"):
    var conf: Either[ConfigError, Conf] = Left(MissingValue(NonEmptyList.of("password")))
    def apply(): Conf = conf.fold(err => throw err, ok => ok)

    override def beforeAll(): Unit =
      conf = testConf
        .parse[Password]("pics.db.pass")
        .map: pass =>
          testDatabaseConf(pass)

    override def afterAll(): Unit = ()

    private def testDatabaseConf(password: Password) = Conf(
      url"jdbc:mysql://127.0.0.1:3306/testpics",
      "testpics",
      password,
      Conf.MySQLDriver,
      maxPoolSize = 2,
      autoMigrate = true
    )

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(db)

case class ServerTools(server: Server):
  def port = server.address.getPort
  def baseHttpUrl = FullUrl("http", s"localhost:$port", "")
  def baseWsUrl = FullUrl("ws", s"localhost:$port", "")

trait ServerSuite extends MUnitDatabaseSuite with ClientSuite:
  self: munit.CatsEffectSuite =>
  object TestServer extends AppResources:
    LogbackUtils.init(rootLevel = Level.OFF)

  private def testPicsConf =
    for
      database <- IO.delay(db())
      conf <- IO.fromEither(
        testConf
          .parse[ConfigNode]("pics")
          .flatMap: node =>
            PicsConf.load(node, _ => database, isTest = true)
      )
    yield conf

  val server = ResourceSuiteLocalFixture(
    "server",
    for
      conf <- Resource.eval(testPicsConf)
      s <- TestServer.server(conf, Resource.eval(MultiSizeHandler.empty()), port = port"12345")
    yield ServerTools(s)
  )

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(db, server, client)

trait ClientSuite:
  self: munit.CatsEffectSuite =>
  val client =
    ResourceSuiteLocalFixture("client", HttpClientIO.resource[IO])

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(client)

trait DoobieSuite extends MUnitDatabaseSuite:
  self: munit.CatsEffectSuite =>
  val doobie: IOFixture[DoobieDatabase[IO]] =
    ResourceSuiteLocalFixture(
      "doobie",
      Resource
        .eval(IO.delay(db()))
        .flatMap: d =>
          DoobieDatabase.init(d)
    )

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(db, doobie)
