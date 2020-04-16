package tests

import java.io.File

import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.pics._
import com.malliina.pics.app.{AppConf, BaseComponents}
import com.malliina.pics.auth.PicsAuthLike
import com.malliina.pics.db.Conf
import com.malliina.play.auth.{AuthConf, JWTUser}
import com.malliina.values.AccessToken
import controllers.Social.SocialConf
import play.api.ApplicationLoader.Context
import play.api.mvc.{RequestHeader, Result}
import play.api.{ApplicationLoader, Environment, Mode, Play}

import scala.concurrent.Future

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

class TestComps(context: Context, creds: GoogleOAuthCredentials, database: Conf)
  extends BaseComponents(context, _ => creds, _ => AppConf(database)) {
  override def buildAuthenticator() = TestAuthenticator

  override def buildPics() = MultiSizeHandler.clones(TestHandler)

  val fakeConf = AuthConf("", "")
  val fakeSocialConf =
    SocialConf(fakeConf, fakeConf, fakeConf, fakeConf, fakeConf, apple = fakeConf)
  override lazy val socialConf: SocialConf = fakeSocialConf
}

trait MUnitAppSuite { self: munit.Suite =>
  val app: Fixture[TestComps] = new Fixture[TestComps]("pics-app") {
    private var container: Option[MySQLContainer] = None
    private var comps: TestComps = null
    def apply() = comps
    override def beforeAll(): Unit = {
      val db = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
      db.start()
      container = Option(db)
      comps = new TestComps(
        TestConf.createTestAppContext,
        GoogleOAuthCredentials("id", "secret", "scope"),
        TestConf(db)
      )
      Play.start(comps.application)
    }
    override def afterAll(): Unit = {
      Play.stop(comps.application)
      container.foreach(_.stop())
    }
  }

  override def munitFixtures = Seq(app)
}

trait MUnitDatabaseSuite { self: munit.Suite =>
  val db: Fixture[MySQLContainer] = new Fixture[MySQLContainer]("database") {
    var container: MySQLContainer = null
    def apply() = container
    override def beforeAll(): Unit = {
      container = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
      container.start()
    }
    override def afterAll(): Unit = {
      container.stop()
    }
  }

  override def munitFixtures = Seq(db)
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
