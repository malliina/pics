package com.malliina.pics.auth

import com.malliina.web.AuthError
import org.http4s.Headers

class MissingCredentialsException(error: MissingCredentials2) extends IdentityException(error)

class IdentityException(val error: IdentityError2) extends Exception

object IdentityException {
  def apply(error: IdentityError2): IdentityException = error match {
    case mc @ MissingCredentials2(_, _) => new MissingCredentialsException(mc)
    case other                          => new IdentityException(other)
  }
}

sealed trait IdentityError2 {
  def headers: Headers
}

case class MissingCredentials2(message: String, headers: Headers) extends IdentityError2
case class JWTError2(error: AuthError, headers: Headers) extends IdentityError2
