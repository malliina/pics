package controllers

import cats.effect.IO
import com.malliina.http.HttpClient
import com.malliina.pics.auth.{EmailUser, GoogleTokenAuth, Validators}
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, IdToken, TokenValue}
import com.malliina.web.*
import controllers.JWTAuth.log

object JWTAuth:
  private val log = AppLogger(getClass)

  def default(http: HttpClient[IO]) =
    new JWTAuth(Validators.picsAccess, Validators.picsId, Validators.google(http))

class JWTAuth(
  val ios: CognitoAccessValidator,
  android: CognitoIdValidator,
  google: GoogleTokenAuth
):
  def validateUser(token: TokenValue): IO[Either[AuthError, JWTUser]] =
    IO
      .pure(
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
          ok => IO.pure(Right(ok))
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
    * @param token
    *   access token or id token
    */
  def validateToken(token: TokenValue): IO[Either[AuthError, JWTUser]] =
    validateUser(token)
