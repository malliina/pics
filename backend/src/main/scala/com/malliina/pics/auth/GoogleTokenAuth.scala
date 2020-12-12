package com.malliina.pics.auth

import com.malliina.http.OkClient
import com.malliina.web.{AuthError, ClientId, GoogleAuthFlow, InvalidClaims, KeyClient}
import com.malliina.values.{Email, ErrorMessage, IdToken}

import scala.concurrent.{ExecutionContext, Future}

object GoogleTokenAuth {

  /** The Android app uses the web client ID while the iOS app uses the iOS client ID.
    *
    * @param webClientId android apps
    * @param iosClientId ios apps
    */
  def apply(
    webClientId: ClientId,
    iosClientId: ClientId,
    http: OkClient
  ): GoogleTokenAuth =
    new GoogleTokenAuth(GoogleAuthFlow.keyClient(Seq(webClientId, iosClientId), http))(http.exec)
}

/** Validates Google ID tokens and extracts the email address.
  */
class GoogleTokenAuth(validator: KeyClient)(implicit val ec: ExecutionContext) {
  val EmailKey = "email"
  val EmailVerified = "email_verified"

//  def authEmail(rh: RequestHeader): Future[Email] =
//    Auth
//      .readAuthToken(rh)
//      .map { token =>
//        validate(IdToken(token)).flatMap { e =>
//          e.fold(
//            err => Future.failed(IdentityException(JWTError(rh, err))),
//            email => Future.successful(email)
//          )
//        }
//      }
//      .getOrElse {
//        Future.failed(IdentityException(MissingCredentials(rh)))
//      }

  def validate(token: IdToken): Future[Either[AuthError, Email]] =
    validator.validate(token).map { outcome =>
      outcome.flatMap { v =>
        val parsed = v.parsed
        parsed.read(parsed.claims.getBooleanClaim(EmailVerified), EmailVerified).flatMap {
          isVerified =>
            if (isVerified) parsed.readString(EmailKey).map(Email.apply)
            else Left(InvalidClaims(token, ErrorMessage("Email not verified.")))
        }
      }
    }
}
