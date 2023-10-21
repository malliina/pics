package com.malliina.pics.auth

import cats.effect.Sync
import cats.syntax.all.*
import com.malliina.http.HttpClient
import com.malliina.values.{Email, ErrorMessage, IdToken}
import com.malliina.web.*

object GoogleTokenAuth:

  /** The Android app uses the web client ID while the iOS app uses the iOS client ID.
    *
    * @param webClientId
    *   android apps
    * @param iosClientId
    *   ios apps
    */
  def default[F[_]: Sync](
    webClientId: ClientId,
    iosClientId: ClientId,
    http: HttpClient[F]
  ): GoogleTokenAuth[F] =
    val keyClient = GoogleAuthFlow.keyClient(Seq(webClientId, iosClientId), http)
    GoogleTokenAuth(keyClient)

/** Validates Google ID tokens and extracts the email address.
  */
class GoogleTokenAuth[F[_]: Sync](validator: KeyClient[F]):
  val EmailKey = "email"
  val EmailVerified = "email_verified"

  def validate(token: IdToken): F[Either[AuthError, Email]] =
    validator
      .validate(token)
      .map: outcome =>
        outcome.flatMap: v =>
          val parsed = v.parsed
          parsed
            .read(parsed.claims.getBooleanClaim(EmailVerified), EmailVerified)
            .flatMap: isVerified =>
              if isVerified then parsed.readString(EmailKey).map(Email.apply)
              else Left(InvalidClaims(token, ErrorMessage("Email not verified.")))
