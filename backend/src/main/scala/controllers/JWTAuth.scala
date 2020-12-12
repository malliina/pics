package controllers

import com.malliina.http.OkClient
import com.malliina.pics.auth.{EmailUser, GoogleTokenAuth}
import com.malliina.play.auth.Validators
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, IdToken, TokenValue}
import com.malliina.web._
import controllers.JWTAuth.log

import scala.concurrent.{ExecutionContext, Future}

object JWTAuth {
  private val log = AppLogger(getClass)

  def default(http: OkClient) =
    new JWTAuth(Validators.picsAccess, Validators.picsId, Validators.google(http))
}

class JWTAuth(
  val ios: CognitoAccessValidator,
  android: CognitoIdValidator,
  google: GoogleTokenAuth
) {
  implicit val ec: ExecutionContext = google.ec

  def validateUser(token: TokenValue): Future[Either[AuthError, JWTUser]] =
    Future
      .successful(
        ios.validate(AccessToken(token.value)).orElse(android.validate(IdToken(token.value)))
      )
      .flatMap { e =>
        e.fold(
          err =>
            google.validate(IdToken(token.value)).map { e =>
              e.map { email =>
                EmailUser(email)
              }
            },
          ok => Future.successful(Right(ok))
        )
      }
      .map { e =>
        e.left.map { error =>
          log.warn(s"JWT validation failed: '${error.message}'. Token: '$token'.")
          error
        }
      }

  /** User/pass login in iOS uses access tokens but social login in Android uses ID tokens.
    *
    * @param token access token or id token
    */
  def validateToken(token: TokenValue): Future[Either[AuthError, JWTUser]] =
    validateUser(token)
}
