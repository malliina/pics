package controllers

import com.malliina.pics.auth.{AccessToken, TokenValidator, Verified}
import com.malliina.pics.{Errors, SingleError}
import play.api.mvc.{AbstractController, ControllerComponents, EssentialAction, RequestHeader}

class JWTController(validator: TokenValidator, comps: ControllerComponents) extends AbstractController(comps) {
  def authed(f: Verified => EssentialAction): EssentialAction = EssentialAction { rh =>
    val action: EssentialAction =
      readToken(rh).map { token =>
        validator.validate(token).fold(
          err => Action(fail(SingleError.forJWT(err))),
          ok => f(ok)
        )
      }.getOrElse {
        Action(fail(SingleError(s"JWT token missing. Use the '$AUTHORIZATION' header.")))
      }
    action(rh)
  }

  def fail(err: SingleError) = Unauthorized(Errors(Seq(err)))

  def readToken[T](rh: RequestHeader, expectedSchema: String = "Bearer"): Option[AccessToken] = {
    rh.headers.get(AUTHORIZATION) flatMap { authInfo =>
      authInfo.split(" ") match {
        case Array(schema, encodedCredentials) if schema == expectedSchema =>
          Option(AccessToken(encodedCredentials))
        case _ =>
          None
      }
    }
  }
}
