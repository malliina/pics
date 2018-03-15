package controllers

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.http.AsyncHttp
import com.malliina.pics.auth._
import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._

object Social {
  private val log = Logger(getClass)

  def buildOrFail(actions: ActionBuilder[Request, AnyContent]) =
    new Social(actions, AuthConf.github, AuthConf.microsoft, AuthConf.google, AuthConf.facebook)
}

class Social(actions: ActionBuilder[Request, AnyContent],
             githubConf: AuthConf,
             microsoftConf: AuthConf,
             googleConf: AuthConf,
             facebookConf: AuthConf) {
  val httpClient = new AsyncHttp()
  val microsoftValidator = StandardCodeValidator(CodeValidationConf.microsoft(routes.Social.microsoftCallback(), microsoftConf, httpClient))
  val gitHubValidator = new GitHubCodeValidator(githubConf, httpClient)
  val googleValidator = StandardCodeValidator(CodeValidationConf.google(routes.Social.googleCallback(), googleConf, httpClient))
  val facebookValidator = new FacebookCodeValidator(routes.Social.facebookCallback(), facebookConf, httpClient)

  def microsoft = start(microsoftValidator)

  def microsoftCallback = callback(microsoftValidator)

  def github = start(gitHubValidator)

  def githubCallback = callback(gitHubValidator)

  def google = start(googleValidator)

  def googleCallback = callback(googleValidator)

  def facebook = start(facebookValidator)

  def facebookCallback = callback(facebookValidator)

  def twitter = actions {
    Ok
  }

  def twitterCallback = actions {
    Ok
  }

  private def start(codeValidator: CodeValidator) = actions.async { req => codeValidator.start(req) }

  private def callback(codeValidator: CodeValidator) = actions.async { req => codeValidator.validateCode(req) }
}
