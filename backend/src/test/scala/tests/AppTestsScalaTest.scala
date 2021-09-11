package tests

import cats.effect.{ContextShift, IO, Timer}
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.pics.*
import com.malliina.pics.PicsConf.ConfigOps
import com.malliina.pics.app.LocalConf
import com.malliina.pics.db.{DatabaseConf, DatabaseRunner, DoobieDatabase}
import com.malliina.pics.http4s.PicsServer
import com.malliina.pics.http4s.PicsServer.AppService
import munit.FunSuite
import org.slf4j.LoggerFactory
import org.testcontainers.utility.DockerImageName
import tests.MUnitDatabaseSuite.log

import scala.concurrent.Promise
import scala.util.Try

case class TestPicsConf(testdb: DatabaseConf)
case class WrappedTestConf(pics: TestPicsConf)

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

import cats.syntax.flatMap.*
// https://github.com/typelevel/munit-cats-effect
trait Http4sSuite extends MUnitDatabaseSuite:
  self: FunSuite =>
  implicit def munitContextShift: ContextShift[IO] =
    IO.contextShift(munitExecutionContext)

  implicit def munitTimer: Timer[IO] =
    IO.timer(munitExecutionContext)

  val app: Fixture[AppService] = new Fixture[AppService]("pics-app2"):
    private var service: Option[AppService] = None
    val promise = Promise[IO[Unit]]()

    override def apply(): AppService = service.get

    override def beforeAll(): Unit =
      val resource = PicsServer.appResource(
        PicsConf.unsafeLoadWith(PicsConf.picsConf, db()),
        MultiSizeHandlerIO.empty()
      )
      val resourceEffect = resource.allocated[IO, AppService]
      val setupEffect =
        resourceEffect.map { case (t, release) =>
          promise.success(release)
          t
        }
          .flatTap(t => IO.pure(()))

      service = Option(await(setupEffect.unsafeToFuture()))

    override def afterAll(): Unit =
      val f = IO
        .pure(())
        .flatMap(_ => IO.fromFuture(IO(promise.future))(munitContextShift).flatten)
        .unsafeToFuture()
      await(f)

  override def munitFixtures: Seq[Fixture[?]] = Seq(db, app)

trait DoobieSuite extends MUnitDatabaseSuite:
  self: munit.Suite =>
  val doobie: Fixture[DatabaseRunner[IO]] = new Fixture[DatabaseRunner[IO]]("doobie"):
    var database: Option[DatabaseRunner[IO]] = None
    def apply(): DatabaseRunner[IO] = database.get
    override def beforeAll(): Unit =
      val cs = IO.contextShift(munitExecutionContext)
      database = Option(DoobieDatabase.withMigrations(db(), cs))
    override def afterAll(): Unit =
      database.foreach(_.close())

  override def munitFixtures: Seq[Fixture[?]] = Seq(db, doobie)

object TestConf:
  def apply(container: MySQLContainer) = DatabaseConf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password
  )
