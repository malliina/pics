package controllers

import com.malliina.http.OkClient
import com.malliina.play.auth._
import controllers.Social.SocialConf
import play.api.mvc.Results.Redirect
import play.api.mvc._
import play.api.{Configuration, Mode}

import scala.util.Try

object Social {
  def buildOrFail(actions: ActionBuilder[Request, AnyContent], socialConf: SocialConf) =
    new Social(actions, socialConf)

  case class SocialConf(githubConf: AuthConf,
                        microsoftConf: AuthConf,
                        googleConf: AuthConf,
                        facebookConf: AuthConf,
                        twitterConf: AuthConf,
                        amazonConf: AuthConf = AuthConf("2rnqepv44epargdosba6nlg2t9", "unused"))

  object SocialConf {

    def forMode(mode: Mode, c: Configuration): SocialConf = {
      val reader =
        if (mode == Mode.Prod) AuthConfReader.conf(c)
        else AuthConfReader.env
      read(reader)
    }

    def apply(conf: Configuration): SocialConf = {
      Try(read(AuthConfReader.env)).getOrElse(read(AuthConfReader.conf(conf)))
    }

    def read(reader: AuthConfReader) = {
      SocialConf(reader.github, reader.microsoft, reader.google, reader.facebook, reader.twitter)
    }
  }

}

class Social(actions: ActionBuilder[Request, AnyContent], conf: SocialConf) {
  val okClient = OkClient.default
  val handler = BasicAuthHandler(routes.PicsController.list())

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

  def microsoftCallback = callback(microsoftValidator)

  def github = start(gitHubValidator)

  def githubCallback = callback(gitHubValidator)

  def google = start(googleValidator)

  def googleCallback = callback(googleValidator)

  def facebook = start(facebookValidator)

  def facebookCallback = callback(facebookValidator)

  def twitter = start(twitterValidator)

  def twitterCallback = callback(twitterValidator)

  def amazon = start(amazonValidator)

  def amazonCallback = callback(amazonValidator)

  private def start(codeValidator: AuthValidator) = actions.async { req => codeValidator.start(req) }

  private def callback(codeValidator: AuthValidator) = actions.async { req => codeValidator.validateCallback(req) }
}
