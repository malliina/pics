package controllers

import java.math.BigInteger
import java.security.SecureRandom

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.http.{AsyncHttp, FullUrl}
import com.malliina.oauth.GoogleOAuth
import com.malliina.pics.auth._
import com.malliina.play.http.FullUrls
import com.malliina.play.json.JsonMessages
import controllers.CognitoControl.log
import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.Future

object CognitoControl {
  private val log = Logger(getClass)

  def pics(actions: ActionBuilder[Request, AnyContent]) =
    new CognitoControl(CognitoIdentityConf.pics, actions)

  def randomState() = new BigInteger(130, new SecureRandom()).toString(32)
}

class CognitoControl(conf: CognitoIdentityConf, actions: ActionBuilder[Request, AnyContent]) {
  val httpClient = new AsyncHttp()
  val State = GoogleOAuth.State
  type AuthState = String

  def google = socialLogin((state, redir) => conf.authUrlGoogle(state, redir))

  def facebook = socialLogin((state, redir) => conf.authUrlFacebook(state, redir))

  def amazon = socialLogin((state, redir) => conf.authUrlAmazon(state, redir))

  def socialLogin(authUrl: (AuthState, FullUrl) => FullUrl) = actions { req =>
    val state = CognitoControl.randomState()
    Redirect(authUrl(state, redirUrl(req)).url).withSession(State -> state)
  }

  def redirUrl(rh: RequestHeader) = FullUrls(routes.CognitoControl.cognitoCallback(), rh)

  /** Called by Cognito when the authentication flow is complete.
    *
    * 1. Checks that the returned `state` matches the one set in the session prior to redirecting to Cognito.
    * 2. Submits the returned `code` to Cognito in order to exchange it for tokens.
    * 3. Validates the returned ID token, then extracts the email as the username.
    * 4. Stores the username in the user session, i.e. logs the user in, and redirects to the front page.
    *
    * This callback algorithm works both for Google and (Login with) Amazon.
    */
  def cognitoCallback = actions.async { req =>
    val requestState = req.getQueryString(State)
    val sessionState = req.session.get(State)
    val isStateOk = requestState.exists(rs => sessionState.contains(rs))
    if (isStateOk) {
      req.getQueryString(GoogleOAuth.Code).map { code =>
        httpClient.postForm(conf.tokensUrl.url, conf.tokenParameters(code, redirUrl(req))).map { res =>
          res.parse[CognitoTokens].fold(
            jsonErrors => {
              val msg = "Tokens response failed JSON validation."
              log.error(s"$msg $jsonErrors")
              BadRequest(JsonMessages.failure(msg))
            },
            tokens => {
              CognitoValidator.picsId.validate(tokens.idToken).fold(
                err => {
                  log.error(s"${err.message} $err")
                  JWTAuth.failJwt(err)
                },
                user => {
                  log.info(s"Social login for '${user.username}' completed.")
                  Redirect(routes.PicsController.list()).withSession("username" -> user.username.name)
                }
              )
            }
          )
        }
      }.getOrElse {
        val msg = "No code in callback."
        log.warn(msg)
        fut(BadRequest(JsonMessages.failure(msg)))
      }
    } else {
      val msg = "Invalid state parameter in OAuth callback."
      log error msg
      fut(Unauthorized(JsonMessages.failure(msg)))
    }
  }

  def fut[T](t: T) = Future.successful(t)
}
