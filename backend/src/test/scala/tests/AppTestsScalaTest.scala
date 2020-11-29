package tests

import java.io.File

import cats.effect.{ContextShift, IO, Timer}
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.pics._
import com.malliina.pics.app.{AppConf, BaseComponents, LocalConf}
import com.malliina.pics.auth.PicsAuthLike
import com.malliina.pics.db.{DatabaseConf, DoobieDatabase}
import com.malliina.pics.http4s.PicsServer
import com.malliina.pics.http4s.PicsServer.AppService
import com.malliina.play.auth.{AuthError, InvalidSignature, JWTUser}
import com.malliina.values.AccessToken
import controllers.Social.SocialConf
import munit.FunSuite
import play.api.ApplicationLoader.Context
import play.api._
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.{Future, Promise}
import scala.util.Try

object TestHandler extends ImageHandler("test", AsIsResizer, TestPics)

object TestAuthenticator extends PicsAuthLike {
  val TestQuery = "u"
  val TestUser = "demouser"

  override def authenticate(rh: RequestHeader): Future[Either[Result, PicRequest]] = {
    val user = rh.getQueryString(TestQuery)
    val res =
      if (user.contains(PicOwner.anon.name)) Right(PicRequest.anon(rh))
      else if (user.contains(TestUser)) Right(PicRequest(PicOwner(user.get), rh))
      else Left(unauth)
    Future.successful(res)
  }

  override def validateToken(token: AccessToken): Future[Either[AuthError, JWTUser]] =
    Future.successful(Left(InvalidSignature(token)))
}

//class TestComps(context: Context, database: DatabaseConf)
//  extends BaseComponents(context, _ => AppConf(database)) {
//  override def buildAuthenticator() = TestAuthenticator
//  override def buildPics() = MultiSizeHandler.clones(TestHandler)
//
//  override lazy val socialConf: SocialConf = SocialConf(configuration)
//}

trait MUnitDatabaseSuite { self: munit.Suite =>
  val db: Fixture[DatabaseConf] = new Fixture[DatabaseConf]("database") {
    var container: Option[MySQLContainer] = None
    var conf: Option[DatabaseConf] = None
    def apply() = conf.get
    override def beforeAll(): Unit = {
      val localTestDb =
        Try(LocalConf.localConf.get[Configuration]("pics.testdb")).toEither.flatMap { c =>
          DatabaseConf.fromDatabaseConf(c)
        }
      val testDb = localTestDb.getOrElse {
        val c = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
        c.start()
        container = Option(c)
        TestConf(c)
      }
      conf = Option(testDb)
    }
    override def afterAll(): Unit = {
      container.foreach(_.stop())
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db)
}

//trait MUnitAppSuite extends MUnitDatabaseSuite { self: munit.Suite =>
//  val app: Fixture[TestComps] = new Fixture[TestComps]("pics-app") {
//    private var comps: Option[TestComps] = None
//    def apply() = comps.get
//    override def beforeAll(): Unit = {
//      val c = new TestComps(TestConf.createTestAppContext, db())
//      comps = Option(c)
//      Play.start(c.application)
//    }
//    override def afterAll(): Unit = {
//      comps.foreach(c => Play.stop(c.application))
//    }
//  }
//
//  override def munitFixtures = Seq(db, app)
//}
import cats.syntax.flatMap._
// https://github.com/typelevel/munit-cats-effect
trait Http4sSuite extends MUnitDatabaseSuite { self: FunSuite =>
  implicit def munitContextShift: ContextShift[IO] =
    IO.contextShift(munitExecutionContext)

  implicit def munitTimer: Timer[IO] =
    IO.timer(munitExecutionContext)

  val app: Fixture[AppService] = new Fixture[AppService]("pics-app2") {
    private var service: Option[AppService] = None
    val promise = Promise[IO[Unit]]()

    override def apply(): AppService = service.get

    override def beforeAll(): Unit = {
      val resource = PicsServer.appResource(PicsConf.load.copy(db = db()))
      val resourceEffect = resource.allocated[IO, AppService]
      val setupEffect =
        resourceEffect
          .map {
            case (t, release) =>
              promise.success(release)
              t
          }
          .flatTap(t => IO.pure(()))

      service = Option(await(setupEffect.unsafeToFuture()))
    }

    override def afterAll(): Unit = {
      val f = IO
        .pure(())
        .flatMap(_ => IO.fromFuture(IO(promise.future))(munitContextShift).flatten)
        .unsafeToFuture()
      await(f)
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db, app)
}

//trait PicsHttp4sSuite extends FunSuite with Http4sSuite with FunFixtures {
//  def fromResource[T](resource: Resource[IO, T]): FunFixture[T] =
//    fromResource(resource, (_, _) => IO.pure(()), _ => IO.pure(()))
//
//  def fromResource[T](
//    resource: Resource[IO, T],
//    setup: (TestOptions, T) => IO[Unit],
//    teardown: T => IO[Unit]
//  ): FunFixture[T] = {
//    val promise = Promise[IO[Unit]]()
//
//    FunFixture.async(
//      setup = { testOptions =>
//        val resourceEffect = resource.allocated[IO, T]
//        val setupEffect =
//          resourceEffect
//            .map {
//              case (t, release) =>
//                promise.success(release)
//                t
//            }
//            .flatTap(t => setup(testOptions, t))
//
//        setupEffect.unsafeToFuture()
//      },
//      teardown = { (argument: T) =>
//        teardown(argument)
//          .flatMap(_ => IO.fromFuture(IO(promise.future))(munitContextShift).flatten)
//          .unsafeToFuture()
//      }
//    )
//  }
//}

trait DoobieSuite extends MUnitDatabaseSuite { self: munit.Suite =>
  val doobie: Fixture[DoobieDatabase] = new Fixture[DoobieDatabase]("doobie") {
    var database: Option[DoobieDatabase] = None
    def apply() = database.get
    override def beforeAll(): Unit = {
      database = Option(DoobieDatabase.withMigrations(db(), munitExecutionContext))
    }
    override def afterAll(): Unit = {
      database.foreach(_.close())
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db, doobie)
}

object TestConf {
  def apply(container: MySQLContainer) = DatabaseConf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password
  )

  def createTestAppContext: Context = {
    val classLoader = ApplicationLoader.getClass.getClassLoader
    val env =
      new Environment(new File("."), classLoader, Mode.Test)
    Context.create(env)
  }
}
