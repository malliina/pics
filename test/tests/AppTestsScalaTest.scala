package tests

import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.pics._
import com.malliina.pics.app.BaseComponents
import com.malliina.pics.auth.{AccessToken, AuthConf, CognitoUser, PicsAuthLike}
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

class TestComps(context: Context, creds: GoogleOAuthCredentials, socialConf: SocialConf)
  extends BaseComponents(context, creds, socialConf) {
  override def buildAuthenticator() = TestAuthenticator

  override def buildPics() = MultiSizeHandler.clones(TestHandler)
}

abstract class TestAppSuite extends AppSuite(ctx => {
  val fakeConf = AuthConf("", "")
  val fakeSocialConf = SocialConf(fakeConf, fakeConf, fakeConf, fakeConf, fakeConf)
  new TestComps(ctx, GoogleOAuthCredentials("id", "secret", "scope"), fakeSocialConf)
})
