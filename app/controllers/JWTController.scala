package controllers

import com.malliina.pics.auth.{AccessToken, JWTError, TokenValidator}
import com.malliina.pics.{Errors, SingleError}
import play.api.mvc.{AbstractController, ControllerComponents, EssentialAction, RequestHeader}

class JWTController[T](validator: TokenValidator[T], comps: ControllerComponents)
  extends AbstractController(comps) {

  def authed(f: T => EssentialAction): EssentialAction =
    tokenAuth(validator.validate)(f)

  def tokenAuth(verify: AccessToken => Either[JWTError, T])(f: T => EssentialAction) =
    EssentialAction { rh =>
      val action =
        readToken(rh).map { token =>
          verify(token).fold(
            err => Action(fail(SingleError.forJWT(err))),
            ok => f(ok)
          )
        }.getOrElse {
          Action(fail(SingleError(s"JWT token missing. Use the '$AUTHORIZATION' header.")))
        }
      action(rh)
    }

  def fail(err: SingleError) = Unauthorized(Errors(Seq(err)))

  def readToken(rh: RequestHeader, expectedSchema: String = "Bearer"): Option[AccessToken] = {
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
