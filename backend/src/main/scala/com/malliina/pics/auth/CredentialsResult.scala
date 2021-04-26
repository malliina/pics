package com.malliina.pics.auth

import com.malliina.values.{AccessToken, IdToken}
import org.http4s.Headers

sealed trait CredentialsResult

object CredentialsResult {
  case class IdTokenResult(token: IdToken) extends CredentialsResult
  case class AccessTokenResult(token: AccessToken) extends CredentialsResult
  case class NoCredentials(headers: Headers) extends CredentialsResult
}
