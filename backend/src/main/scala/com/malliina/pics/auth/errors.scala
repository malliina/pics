package com.malliina.pics.auth

import com.malliina.web.AuthError
import org.http4s.Headers

class MissingCredentialsException(error: MissingCredentials) extends IdentityException(error)

class IdentityException(val error: IdentityError) extends Exception

object IdentityException:
  def apply(error: IdentityError): IdentityException = error match
    case mc @ MissingCredentials(_, _) => new MissingCredentialsException(mc)
    case other                         => new IdentityException(other)

sealed trait IdentityError:
  def headers: Headers

case class MissingCredentials(message: String, headers: Headers) extends IdentityError
case class TokenError(error: AuthError, headers: Headers) extends IdentityError
