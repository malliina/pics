package controllers

import com.malliina.http.OkClient
import com.malliina.play.auth._
import controllers.Social.SocialConf
import play.api.mvc._

object Social {
  def buildOrFail(actions: ActionBuilder[Request, AnyContent], socialConf: SocialConf) =
    new Social(actions, socialConf)

  case class SocialConf(githubConf: AuthConf,
                        microsoftConf: AuthConf,
                        googleConf: AuthConf,
                        facebookConf: AuthConf,
                        twitterConf: AuthConf)

  object SocialConf {
    def apply(): SocialConf = SocialConf(AuthConf.github, AuthConf.microsoft, AuthConf.google, AuthConf.facebook, AuthConf.twitter)
  }

}

class Social(actions: ActionBuilder[Request, AnyContent], conf: SocialConf) {
  val okClient = OkClient.default
  val handler = BasicAuthHandler(routes.PicsController.list())
  val microsoftValidator = StandardCodeValidator(CodeValidationConf.microsoft(routes.Social.microsoftCallback(), handler, conf.microsoftConf, okClient))
  val gitHubValidator = new GitHubCodeValidator(routes.Social.githubCallback(), handler, conf.githubConf, okClient)
  val googleValidator = StandardCodeValidator(CodeValidationConf.google(routes.Social.googleCallback(), handler, conf.googleConf, okClient))
  val facebookValidator = new FacebookCodeValidator(routes.Social.facebookCallback(), handler, conf.facebookConf, okClient)
  val twitterValidator = new TwitterValidator(routes.Social.twitterCallback(), handler, conf.twitterConf, okClient)

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

  private def start(codeValidator: AuthValidator) = actions.async { req => codeValidator.start(req) }

  private def callback(codeValidator: AuthValidator) = actions.async { req => codeValidator.validateCallback(req) }
}
