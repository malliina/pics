package controllers

import com.malliina.concurrent.Execution.cached
import com.malliina.http.OkClient
import com.malliina.pics.Errors
import com.malliina.pics.auth.{AppleCodeValidator, AppleResponse, AppleTokenValidator}
import com.malliina.play.auth.CognitoCodeValidator.IdentityProvider
import com.malliina.play.auth._
import com.malliina.play.http.HttpConstants
import com.malliina.values.Email
import controllers.Social._
import play.api.mvc._
import play.api.{Configuration, Logger}

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

object Social {
  private val log = Logger(getClass)

  val PromptCookie = "picsPrompt"
  val PromptKey = "prompt"
  val ProviderCookie = "picsProvider"
  val LoginCookieDuration: Duration = 3650.days
  val SelectAccount = "select_account"

  val SessionKey = "picsUser"
  val LastIdCookie = "picsLastId"

  def buildOrFail(socialConf: SocialConf, http: OkClient, comps: ControllerComponents) =
    new Social(socialConf, http, comps)

  case class SocialConf(
    githubConf: AuthConf,
    microsoftConf: AuthConf,
    googleConf: AuthConf,
    facebookConf: AuthConf,
    twitterConf: AuthConf,
    amazonConf: AuthConf,
    apple: AuthConf
  )

  object SocialConf {
    def apply(conf: Configuration): SocialConf = {
      val parent = conf.get[Configuration]("pics")
      def creds(node: String) = readCredentials(parent.get[Configuration](node))
      SocialConf(
        creds("github"),
        creds("microsoft"),
        creds("google"),
        creds("facebook"),
        creds("twitter"),
        creds("amazon"),
        creds("apple")
      )
    }

    def readCredentials(conf: Configuration) =
      AuthConf(conf.get[String]("id"), conf.get[String]("secret"))
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
  case object Apple extends AuthProvider("apple")
}

class Social(conf: SocialConf, http: OkClient, comps: ControllerComponents)
  extends AbstractController(comps) {
  val reverse = routes.Social
  val successCall = routes.PicsController.list()
  val handler: AuthHandler = new AuthHandler {
    override def onAuthenticated(email: Email, req: RequestHeader): Result = {
      log.info(s"Logging in '$email' through OAuth code flow.")
      Redirect(successCall)
        .withSession(SessionKey -> email.email)
        .withCookies(Cookie(LastIdCookie, email.email, Option(LoginCookieDuration.toSeconds.toInt)))
        .discardingCookies(DiscardingCookie(PromptCookie))
        .withHeaders(CACHE_CONTROL -> HttpConstants.NoCacheRevalidate)
    }

    override def onUnauthorized(error: AuthError, req: RequestHeader): Result = {
      log.warn(s"Authentication failed. ${error.message}")
      Redirect(routes.PicsController.signIn())
        .discardingCookies(DiscardingCookie(ProviderCookie), DiscardingCookie(LastIdCookie))
        .withNewSession
        .withHeaders(CACHE_CONTROL -> HttpConstants.NoCacheRevalidate)
    }
  }

  object cognitoHandler extends AuthResults[CognitoUser] {
    override def onAuthenticated(user: CognitoUser, req: RequestHeader): Result =
      Redirect(successCall).withSession(SessionKey -> user.username.name)

    override def onUnauthorized(error: AuthError, req: RequestHeader): Result =
      handler.onUnauthorized(error, req)
  }

  val microsoftValidator = MicrosoftCodeValidator(
    oauthConf(reverse.microsoftCallback(), conf.microsoftConf)
  )
  val gitHubValidator = GitHubCodeValidator(
    oauthConf(reverse.githubCallback(), conf.githubConf)
  )
  val googleValidator = GoogleCodeValidator(
    oauthConf(reverse.googleCallback(), conf.googleConf)
  )
  val facebookValidator = FacebookCodeValidator(
    oauthConf(reverse.facebookCallback(), conf.facebookConf)
  )
  val twitterValidator = TwitterValidator(
    oauthConf(reverse.twitterCallback(), conf.twitterConf)
  )
  val appleValidator = AppleCodeValidator(
    oauthConf(reverse.appleCallback(), conf.apple),
    AppleTokenValidator(Seq(ClientId(conf.apple.clientId)))
  )

  private val amazonOauth =
    OAuthConf(routes.Social.amazonCallback(), cognitoHandler, conf.amazonConf, http)
  val amazonValidator = cognitoValidator(IdentityProvider.LoginWithAmazon)
  val googleViaAmazonValidator = cognitoValidator(IdentityProvider.IdentityGoogle)

  def cognitoValidator(identityProvider: IdentityProvider) = CognitoCodeValidator(
    "pics.auth.eu-west-1.amazoncognito.com",
    identityProvider,
    Validators.picsId,
    amazonOauth
  )

  def oauthConf(redirCall: Call, conf: AuthConf) = OAuthConf(redirCall, handler, conf, http)

  def microsoft = startHinted(microsoftValidator)
  def microsoftCallback = callback(microsoftValidator, Microsoft)

  def github = start(gitHubValidator)
  def githubCallback = callback(gitHubValidator, GitHub)

  def google = startHinted(googleValidator)
  def googleCallback = callback(googleValidator, Google)

  def facebook = start(facebookValidator)
  def facebookCallback = callback(facebookValidator, Facebook)

  def twitter = start(twitterValidator)
  def twitterCallback = callback(twitterValidator, Twitter)

  def amazon = start(amazonValidator)
  def amazonCallback = callback(amazonValidator, Amazon)

  def googleViaAmazon = start(googleViaAmazonValidator)
  def googleViaAmazonCallack = callback(googleViaAmazonValidator, Amazon)

  def apple = start(appleValidator)

  def appleCallback = Action(parse.formUrlEncoded).async { req =>
    AppleResponse(req.body).fold(
      err => {
        val msg = s"Failed to parse Apple response: '$err'."
        log.error(msg)
        fut(BadRequest(Errors.single(msg)))
      },
      response => {
        val actualState = response.state
        val sessionState = req.session.get(OAuthKeys.State)
        val isStateOk = sessionState.contains(actualState)
        if (isStateOk) {
          appleValidator.validate(response.code, req).map { outcome =>
            appleValidator.onOutcome(outcome, req)
          }
        } else {
          val detailed = sessionState.fold(s"Got '$actualState' but found nothing to compare to.") {
            expected =>
              s"Got '$actualState' but expected '$expected'."
          }
          log.error(s"Authentication failed, state mismatch. $detailed $req")
          fut(appleValidator.onOutcome(Left(OAuthError("State mismatch.")), req))
        }
      }
    )
  }

  private def fut[T](t: T) = Future.successful(t)

  private def start(codeValidator: AuthValidator): Action[AnyContent] =
    Action.async { req =>
      codeValidator.start(req, Map.empty)
    }

  private def startHinted(codeValidator: LoginHintSupport) =
    Action.async { req =>
      val promptCookie = req.cookies.get(PromptCookie)
      val extra = promptCookie.map(c => Map(PromptKey -> c.value)).getOrElse(Map.empty)
      val maybeEmail = req.cookies.get(LastIdCookie).map(_.value).filter(_ => extra.isEmpty)
      maybeEmail.foreach { hint =>
        log.info(s"Starting OAuth flow with login hint '$hint'.")
      }
      promptCookie.foreach { c =>
        log.info(s"Starting OAuth flow with prompt '${c.value}'.")
      }
      codeValidator.startHinted(req, maybeEmail, extra)
    }

  private def callback(codeValidator: AuthValidator, provider: AuthProvider): Action[AnyContent] =
    Action.async { req =>
      codeValidator.validateCallback(req).map { r =>
        val cookie = Cookie(
          ProviderCookie,
          provider.name,
          maxAge = Option(LoginCookieDuration.toSeconds.toInt)
        )
        if (r.header.status < 400) r.withCookies(cookie)
        else r
      }
    }

//  def close(): Unit = http.close()
}
