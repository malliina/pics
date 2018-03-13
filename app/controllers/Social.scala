package controllers

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.http.AsyncHttp
import com.malliina.pics.auth._
import com.malliina.play.http.FullUrls
import com.malliina.play.json.JsonMessages
import controllers.Social.{Conf, log}
import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.Future

object Social {
  private val log = Logger(getClass)

  case class Conf(clientId: String, clientSecret: String)

  object Conf {

    def read(key: String) = sys.env.get(key).orElse(sys.props.get(key))
      .toRight(s"Key missing: '$key'. Set it as an environment variable or system property.")

    def orFail(read: Either[String, Conf]) = read.fold(err => throw new Exception(err), identity)

    def github = readConf("github_client_id", "github_client_secret")

    def microsoft = readConf("microsoft_client_id", "microsoft_client_secret")

    def readConf(clientIdKey: String, clientSecretKey: String) = {
      val attempt = for {
        clientId <- read(clientIdKey)
        clientSecret <- read(clientSecretKey)
      } yield Conf(clientId, clientSecret)
      orFail(attempt)
    }
  }

  def buildOrFail(actions: ActionBuilder[Request, AnyContent]) =
    new Social(actions, Conf.github, Conf.microsoft)
}

class Social(actions: ActionBuilder[Request, AnyContent], githubConf: Conf, microsoftConf: Conf) {
  val httpClient = new AsyncHttp()
  val microsoftValidator = new MicrosoftCodeValidator(routes.Social.microsoftCallback(), microsoftConf, httpClient)
  val gitHubValidator = new GitHubCodeValidator(githubConf, httpClient)

  val ClientId = "client_id"
  val ClientSecret = "client_secret"
  val CodeKey = "code"
  val EmailKey = "email"
  val IdTokenKey = "id_token"
  val Nonce = "nonce"
  val RedirectUri = "redirect_uri"
  val Scope = "scope"
  val State = "state"

  def microsoft = actions.async { req => microsoftValidator.start(req) }

  def microsoftCallback = actions.async { req => microsoftValidator.validate(req) }

  def github = actions.async { req => gitHubValidator.start(req) }

  def githubCallback = actions.async { req => gitHubValidator.validate(req) }

  def twitter = actions {
    Ok
  }

  def twitterCallback = actions(Ok)

  def failWith(message: String) = {
    log.error(message)
    BadRequest(JsonMessages.failure(message))
  }

  def failWithFut(message: String) = {
    fut(failWith(message))
  }

  def stringify(map: Map[String, String]) =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  def fut[T](t: T) = Future.successful(t)

  def redirUrl(call: Call, rh: RequestHeader) = FullUrls(call, rh)
}
