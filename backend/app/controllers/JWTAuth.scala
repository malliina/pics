package controllers

import com.malliina.pics.{Errors, PicRequest, SingleError}
import com.malliina.play.auth._
import controllers.JWTAuth.log
import play.api.Logger
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc._

object JWTAuth {
  private val log = Logger(getClass)

  val default = new JWTAuth(CognitoValidators.picsAccess)

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

class JWTAuth(val validator: CognitoAccessValidator) {
  def userOrAnon(rh: RequestHeader): Either[Result, PicRequest] =
    auth(rh)
      .map(e => e.map(u => PicRequest.forUser(u.username, rh)))
      .getOrElse(Right(PicRequest.anon(rh)))

  def jwtAuth(rh: RequestHeader): Either[Result, JWTUser] =
    auth(rh).getOrElse(missingToken())

  def auth(rh: RequestHeader): Option[Either[Result, CognitoUser]] =
    readToken(rh).map(validateToken)

  def validateToken(token: AccessToken): Either[Result, CognitoUser] =
    validator.validate(token).left.map { error =>
      log.warn(s"JWT validation failed: '${error.message}'. Token: '$token'.")
      fail(SingleError.forJWT(error))
    }

  def fail(err: SingleError) = JWTAuth.failSingle(err)

  def readToken(rh: RequestHeader, expectedSchema: String = "Bearer"): Option[AccessToken] =
    JWTAuth.readToken(rh, expectedSchema)

  def missingToken() = {
    val message = s"JWT token missing. Use the '$AUTHORIZATION' header."
    log.warn(message)
    Left(fail(SingleError(message)))
  }
}
