package controllers

import com.malliina.pics.auth.{AccessToken, JWTError, TokenValidator}
import com.malliina.pics.{Errors, SingleError}
import controllers.JWTController.log
import play.api.Logger
import play.api.mvc.{AbstractController, ControllerComponents, EssentialAction, RequestHeader}

object JWTController {
  private val log = Logger(getClass)
}

class JWTController[T](validator: TokenValidator[T], comps: ControllerComponents)
  extends AbstractController(comps) {

  def authed(f: T => EssentialAction): EssentialAction =
    tokenAuth(validator.validate)(f)

  def tokenAuth(verify: AccessToken => Either[JWTError, T])(f: T => EssentialAction) =
    EssentialAction { rh =>
      val action =
        readToken(rh).map { token =>
          verify(token).fold(
            err => {
              log.warn(s"JWT validation failed: '${err.message}'.")
              Action(fail(SingleError.forJWT(err)))
            },
            ok => {
              f(ok)
            }
          )
        }.getOrElse {
          val message = s"JWT token missing. Use the '$AUTHORIZATION' header."
          log.warn(message)
          Action(fail(SingleError(message)))
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
