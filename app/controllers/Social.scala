package controllers

import com.malliina.concurrent.Execution.cached
import com.malliina.http.OkClient
import com.malliina.play.auth.CodeValidator.LoginHint
import com.malliina.play.auth._
import controllers.Social._
import play.api.mvc.Results.Redirect
import play.api.mvc._
import play.api.Configuration

import scala.util.Try

object Social {
  val ProviderCookie = "provider"

  def buildOrFail(actions: ActionBuilder[Request, AnyContent], socialConf: SocialConf) =
    new Social(actions, socialConf)

  case class SocialConf(githubConf: AuthConf,
                        microsoftConf: AuthConf,
                        googleConf: AuthConf,
                        facebookConf: AuthConf,
                        twitterConf: AuthConf,
                        amazonConf: AuthConf = AuthConf("2rnqepv44epargdosba6nlg2t9", "unused"))

  object SocialConf {
    def apply(conf: Configuration): SocialConf =
      Try(read(AuthConfReader.env)).getOrElse(read(AuthConfReader.conf(conf)))

    def read(reader: AuthConfReader) =
      SocialConf(reader.github, reader.microsoft, reader.google, reader.facebook, reader.twitter)
  }

  sealed abstract class AuthProvider(val name: String)

  object AuthProvider {
    def forString(s: String): Either[String, AuthProvider] =
      Seq(Google, Microsoft, Amazon, Twitter, Facebook, GitHub)
        .find(_.name == s)
        .toRight(s"Unknown auth provider: '$s'.")
  }

  case object Google extends AuthProvider("google")

  case object Microsoft extends AuthProvider("microsoft")

  case object Amazon extends AuthProvider("amazon")

  case object Twitter extends AuthProvider("twitter")

  case object Facebook extends AuthProvider("facebook")

  case object GitHub extends AuthProvider("github")

}

class Social(actions: ActionBuilder[Request, AnyContent], conf: SocialConf) {
  val okClient = OkClient.default
  val lastIdKey = BasicAuthHandler.LastIdCookie
  val handler = BasicAuthHandler(routes.PicsController.list(), lastIdKey)

  object cognitoHandler extends AuthHandlerBase[CognitoUser] {
    override def onAuthenticated(user: CognitoUser, req: RequestHeader): Result =
      Redirect(handler.successCall).withSession(handler.sessionKey -> user.username.name)

    override def onUnauthorized(error: AuthError, req: RequestHeader): Result =
      handler.onUnauthorized(error, req)
  }

  val cognitoConf = CognitoCodeValidator.conf("pics.auth.eu-west-1.amazoncognito.com", conf.amazonConf)
  val microsoftValidator = StandardCodeValidator(CodeValidationConf.microsoft(routes.Social.microsoftCallback(), handler, conf.microsoftConf, okClient))
  val gitHubValidator = new GitHubCodeValidator(routes.Social.githubCallback(), handler, conf.githubConf, okClient)
  val googleValidator = StandardCodeValidator(CodeValidationConf.google(routes.Social.googleCallback(), handler, conf.googleConf, okClient))
  val facebookValidator = new FacebookCodeValidator(routes.Social.facebookCallback(), handler, conf.facebookConf, okClient)
  val twitterValidator = new TwitterValidator(routes.Social.twitterCallback(), handler, conf.twitterConf, okClient)
  val amazonValidator = new CognitoCodeValidator(CognitoCodeValidator.IdentityAmazon, CognitoValidators.picsId, routes.Social.amazonCallback(), cognitoHandler, cognitoConf, okClient)

  def microsoft = start(microsoftValidator)

  def microsoftCallback = callback(microsoftValidator, Microsoft)

  def github = start(gitHubValidator)

  def githubCallback = callback(gitHubValidator, GitHub)

  def google = actions.async { req =>
    val loginHint = req.cookies.get(lastIdKey).map(c => Map(LoginHint -> c.value)).getOrElse(Map.empty)
    googleValidator.start(req, loginHint)
  }

  def googleCallback = callback(googleValidator, Google)

  def facebook = start(facebookValidator)

  def facebookCallback = callback(facebookValidator, Facebook)

  def twitter = start(twitterValidator)

  def twitterCallback = callback(twitterValidator, Twitter)

  def amazon = start(amazonValidator)

  def amazonCallback = callback(amazonValidator, Amazon)

  private def start(codeValidator: AuthValidator, params: Map[String, String] = Map.empty) =
    actions.async { req => codeValidator.start(req, params) }

  private def callback(codeValidator: AuthValidator, provider: AuthProvider): Action[AnyContent] =
    actions.async { req =>
      codeValidator.validateCallback(req).map { r =>
        if (r.header.status < 400) r.withCookies(Cookie(ProviderCookie, provider.name))
        else r
      }
    }
}
