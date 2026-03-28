package com.malliina.pics

case class NavLang(title: String)

case class LoginLang(
  title: String,
  signIn: String,
  signInWith: String,
  signUp: String,
  email: String,
  password: String,
  forgotPassword: String,
  sendCode: String,
  resendCode: String,
  code: String,
  confirm: String,
  newPassword: String,
  reset: String,
  submit: String
)

case class ProfileLang(title: String)

case class Lang(nav: NavLang, login: LoginLang, profile: ProfileLang)

object Lang:
  val en = Lang(
    NavLang("Pics"),
    LoginLang(
      "Pics - Sign up",
      "Sign in",
      "Sign in with",
      "Sign up",
      "Email address",
      "Password",
      "Forgot password?",
      "Send code",
      "Resend code",
      "Code",
      "Confirm",
      "New password",
      "Reset",
      "Submit"
    ),
    ProfileLang("Pics - Profile")
  )
