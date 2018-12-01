package com.malliina.pics

object LoginStrings extends LoginStrings

trait LoginStrings {
  val AuthFailed = "Authentication failed."

  val GoogleSignInCallback = "onGoogleSignIn"
  val GoogleSignOutId = "signOutButton"

  val LoginFormId = "login-form"
  val EmailId = "email"
  val ErrorId = "error"
  val MfaRequired = "MFA required."
  val PasswordId = "password"
  val TokenId = "token"
}
