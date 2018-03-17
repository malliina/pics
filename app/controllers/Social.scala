package controllers

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.http.AsyncHttp
import com.malliina.pics.auth._
import play.api.mvc._

object Social {
  def buildOrFail(actions: ActionBuilder[Request, AnyContent]) =
    new Social(actions, AuthConf.github, AuthConf.microsoft, AuthConf.google, AuthConf.facebook, AuthConf.twitter)
}

class Social(actions: ActionBuilder[Request, AnyContent],
             githubConf: AuthConf,
             microsoftConf: AuthConf,
             googleConf: AuthConf,
             facebookConf: AuthConf,
             twitterConf: AuthConf) {
  val httpClient = new AsyncHttp()
  val microsoftValidator = StandardCodeValidator(CodeValidationConf.microsoft(routes.Social.microsoftCallback(), microsoftConf, httpClient))
  val gitHubValidator = new GitHubCodeValidator(githubConf, httpClient)
  val googleValidator = StandardCodeValidator(CodeValidationConf.google(routes.Social.googleCallback(), googleConf, httpClient))
  val facebookValidator = new FacebookCodeValidator(routes.Social.facebookCallback(), facebookConf, httpClient)
  val twitterValidator = new TwitterValidator(routes.Social.twitterCallback(), twitterConf, httpClient)

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
