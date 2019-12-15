package tests

import ch.vorburger.mariadb4j.{DB, DBConfigurationBuilder}
import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.pics._
import com.malliina.pics.app.{AppConf, BaseComponents, EmbeddedMySQL}
import com.malliina.pics.auth.PicsAuthLike
import com.malliina.pics.db.Conf
import com.malliina.play.auth.{AccessToken, AuthConf, CognitoUser}
import controllers.Social.SocialConf
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

  override def validateToken(token: AccessToken): Either[Result, CognitoUser] =
    Left(unauth)
}

class TestComps(context: Context, creds: GoogleOAuthCredentials)
  extends BaseComponents(context, _ => creds, _ => AppConf(EmbeddedMySQL.temporary)) {
  override def buildAuthenticator() = TestAuthenticator

  override def buildPics() = MultiSizeHandler.clones(TestHandler)

  val fakeConf = AuthConf("", "")
  val fakeSocialConf = SocialConf(fakeConf, fakeConf, fakeConf, fakeConf, fakeConf)
  override lazy val socialConf: SocialConf = fakeSocialConf
}

abstract class TestAppSuite
  extends AppSuite(ctx => {
    new TestComps(ctx, GoogleOAuthCredentials("id", "secret", "scope"))
  })
