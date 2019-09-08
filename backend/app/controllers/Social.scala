package controllers

import com.malliina.concurrent.Execution.cached
import com.malliina.http.OkClient
import com.malliina.play.auth.CognitoCodeValidator.IdentityProvider
import com.malliina.play.auth._
import com.malliina.play.http.HttpConstants
import com.malliina.values.Email
import controllers.Social._
import play.api.http.HeaderNames.CACHE_CONTROL
import play.api.mvc.Results.Redirect
import play.api.mvc._
import play.api.{Configuration, Logger}

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

  def buildOrFail(actions: ActionBuilder[Request, AnyContent], socialConf: SocialConf) =
    new Social(actions, socialConf)

  case class SocialConf(
      githubConf: AuthConf,
      microsoftConf: AuthConf,
      googleConf: AuthConf,
      facebookConf: AuthConf,
      twitterConf: AuthConf,
      amazonConf: AuthConf = AuthConf("2rnqepv44epargdosba6nlg2t9", "unused")
  )

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
    oauthConf(routes.Social.microsoftCallback(), conf.microsoftConf)
  )
  val gitHubValidator = GitHubCodeValidator(
    oauthConf(routes.Social.githubCallback(), conf.githubConf)
  )
  val googleValidator = GoogleCodeValidator(
    oauthConf(routes.Social.googleCallback(), conf.googleConf)
  )
  val facebookValidator = FacebookCodeValidator(
    oauthConf(routes.Social.facebookCallback(), conf.facebookConf)
  )
  val twitterValidator = TwitterValidator(
    oauthConf(routes.Social.twitterCallback(), conf.twitterConf)
  )
  private val amazonOauth =
    OAuthConf(routes.Social.amazonCallback(), cognitoHandler, conf.amazonConf, okClient)
  val amazonValidator = CognitoCodeValidator(
    "pics.auth.eu-west-1.amazoncognito.com",
    IdentityProvider.LoginWithAmazon,
    CognitoValidators.picsId,
    amazonOauth
  )

  def oauthConf(redirCall: Call, conf: AuthConf) = OAuthConf(redirCall, handler, conf, okClient)

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

  private def start(codeValidator: AuthValidator) =
    actions.async { req =>
      codeValidator.start(req, Map.empty)
    }

  private def startHinted(codeValidator: LoginHintSupport) =
    actions.async { req =>
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
    actions.async { req =>
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
}
