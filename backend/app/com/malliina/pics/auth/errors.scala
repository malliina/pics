package com.malliina.pics.auth

import com.malliina.play.auth.AuthError
import play.api.mvc.RequestHeader

sealed trait IdentityError
case class MissingCredentials(rh: RequestHeader) extends IdentityError
case class JWTError(rh: RequestHeader, error: AuthError) extends IdentityError

class MissingCredentialsException(error: MissingCredentials) extends IdentityException(error) {
  def rh = error.rh
}

class IdentityException(val error: IdentityError) extends Exception

object IdentityException {
  def apply(error: IdentityError): IdentityException = error match {
    case mc @ MissingCredentials(_) => new MissingCredentialsException(mc)
    case other                      => new IdentityException(other)
  }

  def missingCredentials(rh: RequestHeader): MissingCredentialsException =
    new MissingCredentialsException(MissingCredentials(rh))
}
