package com.malliina.pics.auth

import com.malliina.values.{AccessToken, IdToken}
import org.http4s.Headers

enum CredentialsResult:
  case IdTokenResult(token: IdToken) extends CredentialsResult
  case AccessTokenResult(token: AccessToken) extends CredentialsResult
  case NoCredentials(headers: Headers) extends CredentialsResult
