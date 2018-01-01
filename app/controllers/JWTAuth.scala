package controllers

import com.malliina.pics.auth._
import com.malliina.pics.{Errors, PicRequest, SingleError}
import play.api.Logger
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc._

object JWTAuth {
  val default = new JWTAuth(CognitoValidator.default)
}

class JWTAuth(val validator: CognitoValidator) {
  private val log = Logger(getClass)

  def userOrAnon(rh: RequestHeader): Either[Result, PicRequest] =
    auth(rh)
      .map(e => e.map(u => PicRequest(u.username, rh)))
      .getOrElse(Right(PicRequest.anon(rh)))

  def jwtAuth(rh: RequestHeader): Either[Result, JWTUser] =
    auth(rh).getOrElse {
      val message = s"JWT token missing. Use the '$AUTHORIZATION' header."
      log.warn(message)
      Left(fail(SingleError(message)))
    }

  def auth(rh: RequestHeader): Option[Either[Result, CognitoUser]] =
    readToken(rh).map { token =>
      validator.validate(token).left.map { error =>
        log.warn(s"JWT validation failed: '${error.message}'. Token: '$token'.")
        fail(SingleError.forJWT(error))
      }
    }

  def fail(err: SingleError) = Results.Unauthorized(Errors(Seq(err)))

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
