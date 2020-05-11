package controllers

import com.malliina.http.OkClient
import com.malliina.pics.auth.{EmailUser, GoogleTokenAuth}
import com.malliina.pics.{Errors, PicRequest, SingleError}
import com.malliina.play.auth._
import com.malliina.values.{AccessToken, IdToken, TokenValue}
import controllers.JWTAuth.log
import play.api.Logger
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

object JWTAuth {
  private val log = Logger(getClass)

  def default(http: OkClient) =
    new JWTAuth(Validators.picsAccess, Validators.picsId, Validators.google(http))

  def failJwt(error: JWTError) = failSingle(SingleError.forJWT(error))

  def failSingle(error: SingleError) =
    Results.Unauthorized(Errors(Seq(error)))

  def readToken(rh: RequestHeader, expectedSchema: String = "Bearer"): Option[AccessToken] =
    rh.headers.get(AUTHORIZATION) flatMap { authInfo =>
      authInfo.split(" ") match {
        case Array(schema, encodedCredentials) if schema == expectedSchema =>
          Option(AccessToken(encodedCredentials))
        case _ =>
          None
      }
    }
}

class JWTAuth(
  val ios: CognitoAccessValidator,
  android: CognitoIdValidator,
  google: GoogleTokenAuth
) {
  implicit val ec: ExecutionContext = google.ec

  /** Called when authenticating iOS/Android requests.
    *
    * @param rh request
    * @return
    */
  def userOrAnon(rh: RequestHeader): Future[Either[Result, PicRequest]] =
    auth(rh)
      .map { f =>
        f.map { e =>
          e.map { u =>
            PicRequest.forUser(u.username, rh)
          }
        }
      }
      .getOrElse {
        Future.successful(Right(PicRequest.anon(rh)))
      }

  private def auth(rh: RequestHeader): Option[Future[Either[Result, JWTUser]]] =
    readToken(rh).map(token => validateToken(token))

  /** User/pass login in iOS uses access tokens but social login in Android uses ID tokens.
    *
    * @param token access token or id token
    */
  def validateToken(token: TokenValue): Future[Either[Result, JWTUser]] =
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
      .map { convertError(token, _) }

  private def convertError[U](token: TokenValue, e: Either[AuthError, U]) =
    e.left.map { error =>
      log.warn(s"JWT validation failed: '${error.message}'. Token: '$token'.")
      fail(SingleError(error.message.message, error.key))
    }

  private def fail(err: SingleError) = JWTAuth.failSingle(err)

  private def readToken(rh: RequestHeader, expectedSchema: String = "Bearer"): Option[TokenValue] =
    JWTAuth.readToken(rh, expectedSchema)
}
