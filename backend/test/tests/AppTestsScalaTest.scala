package tests

import com.dimafeng.testcontainers.{ForAllTestContainer, MySQLContainer}
import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.pics._
import com.malliina.pics.app.{AppConf, BaseComponents}
import com.malliina.pics.auth.PicsAuthLike
import com.malliina.pics.db.Conf
import com.malliina.play.auth.{AccessToken, AuthConf, CognitoUser, JWTUser}
import controllers.Social.SocialConf
import org.scalatest.FunSuite
import play.api.ApplicationLoader.Context
import play.api.mvc.{RequestHeader, Result}

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

abstract class TestAppSuite
  extends FunSuite
  with tests.OneAppPerSuite2[BaseComponents]
  with ForAllTestContainer {
  override val container = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
  override def createComponents(context: Context): BaseComponents = {
    container.start()
    new TestComps(context, GoogleOAuthCredentials("id", "secret", "scope"), TestConf(container))
  }
}

object TestConf {
  def apply(container: MySQLContainer) = Conf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password,
    container.driverClassName
  )
}
