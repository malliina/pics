package com.malliina.pics.js

import scala.scalajs.js

class CognitoException(val friendlyMessage: String) extends Exception(friendlyMessage)

object CognitoException {
  // Cognito error codes
  def apply(failure: CognitoAuthFailure) = failure.code match {
    case "UserNotConfirmedException" => new NotConfirmedException(failure)
    case "CodeMismatchException"     => new ConfirmException(failure)
    case "UsernameExistsException"   => new UserAlreadyExists(failure)
    case "NotAuthorizedException"    => new NotAuthorizedException(failure)
    case "UserNotFoundException"     => new UserNotFound(failure)
    case "InvalidParameterException" => new CognitoException("Invalid input.")
    case "UnknownError"              => new AuthException("An error occurred.", failure)
    case _                           => new AuthException("Authentication failed.", failure)
  }

  def apply(error: js.Error): CognitoException = new CognitoException(error.message)
}

class TotpRequiredException extends CognitoException("TOTP required.")

class MfaRequiredException extends CognitoException("MFA required.")

class AuthException(friendly: String, val error: CognitoAuthFailure)
    extends CognitoException(friendly)

class NotAuthorizedException(error: CognitoAuthFailure)
    extends AuthException("Incorrect username or password.", error)

class UserNotFound(error: CognitoAuthFailure) extends AuthException("User does not exist.", error)

class NotConfirmedException(error: CognitoAuthFailure)
    extends AuthException("User not confirmed.", error)

class SignUpException(friendly: String, val error: CognitoAuthFailure)
    extends CognitoException(friendly)

class ConfirmException(error: CognitoAuthFailure)
    extends SignUpException("Confirmation failed.", error)

class CodeMismatchException(error: CognitoAuthFailure)
    extends SignUpException("Invalid verification code.", error)

class UserAlreadyExists(error: CognitoAuthFailure)
    extends SignUpException("User already exists.", error)
