package tests

import java.io.File

import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.pics._
import com.malliina.pics.app.{AppConf, BaseComponents, LocalConf}
import com.malliina.pics.auth.PicsAuthLike
import com.malliina.pics.db.{Conf, DoobieDatabase}
import com.malliina.play.auth.JWTUser
import com.malliina.values.AccessToken
import controllers.Social.SocialConf
import play.api.ApplicationLoader.Context
import play.api.mvc.{RequestHeader, Result}
import play.api._

import scala.concurrent.Future
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

  override def validateToken(token: AccessToken): Future[Either[Result, JWTUser]] =
    Future.successful(Left(unauth))
}

class TestComps(context: Context, database: Conf)
  extends BaseComponents(context, _ => AppConf(database)) {
  override def buildAuthenticator() = TestAuthenticator
  override def buildPics() = MultiSizeHandler.clones(TestHandler)

  override lazy val socialConf: SocialConf = SocialConf(configuration)
}

trait MUnitDatabaseSuite { self: munit.Suite =>
  val db: Fixture[Conf] = new Fixture[Conf]("database") {
    var container: Option[MySQLContainer] = None
    var conf: Option[Conf] = None
    def apply() = conf.get
    override def beforeAll(): Unit = {
      val localTestDb =
        Try(LocalConf.localConf.get[Configuration]("pics.testdb")).toEither.flatMap { c =>
          Conf.fromDatabaseConf(c)
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

trait MUnitAppSuite extends MUnitDatabaseSuite { self: munit.Suite =>
  val app: Fixture[TestComps] = new Fixture[TestComps]("pics-app") {
    private var comps: Option[TestComps] = None
    def apply() = comps.get
    override def beforeAll(): Unit = {
      val c = new TestComps(TestConf.createTestAppContext, db())
      comps = Option(c)
      Play.start(c.application)
    }
    override def afterAll(): Unit = {
      comps.foreach(c => Play.stop(c.application))
    }
  }

  override def munitFixtures = Seq(db, app)
}

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
  def apply(container: MySQLContainer) = Conf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password,
    container.driverClassName
  )

  def createTestAppContext: Context = {
    val classLoader = ApplicationLoader.getClass.getClassLoader
    val env =
      new Environment(new File("."), classLoader, Mode.Test)
    Context.create(env)
  }
}
