package com.malliina.pics.auth

import cats.effect.IO
import com.malliina.http.HttpClient
import com.malliina.values.{Email, ErrorMessage, IdToken}
import com.malliina.web._

object GoogleTokenAuth {

  /** The Android app uses the web client ID while the iOS app uses the iOS client ID.
    *
    * @param webClientId android apps
    * @param iosClientId ios apps
    */
  def apply(
    webClientId: ClientId,
    iosClientId: ClientId,
    http: HttpClient[IO]
  ): GoogleTokenAuth =
    new GoogleTokenAuth(GoogleAuthFlow.keyClient(Seq(webClientId, iosClientId), http))
}

/** Validates Google ID tokens and extracts the email address.
  */
class GoogleTokenAuth(validator: KeyClient) {
  val EmailKey = "email"
  val EmailVerified = "email_verified"

  def validate(token: IdToken): IO[Either[AuthError, Email]] =
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
