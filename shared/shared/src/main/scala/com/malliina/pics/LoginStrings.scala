package com.malliina.pics

object LoginStrings extends LoginStrings

trait LoginStrings {
  val LoginFormId = "login-form"
  val EmailId = "email"
  val PasswordId = "password"
  val GoogleSignInCallback = "onGoogleSignIn"
  val GoogleSignOutId = "signOutButton"

  val MfaRequired = "MFA required."
  val AuthFailed = "Authentication failed."
}
