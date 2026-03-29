package com.malliina.pics

case class NavLang(title: String, navigation: String, previous: String, next: String)

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
  submit: String,
  signOut: String
)

case class PicsLang(title: String)
case class DropLang(title: String, dragFilesHere: String, sync: String, delete: String)
case class ProfileLang(title: String)

case class Lang(
  nav: NavLang,
  pics: PicsLang,
  drop: DropLang,
  login: LoginLang,
  profile: ProfileLang
)

object Lang:
  val en = Lang(
    NavLang("Pics", "Navigation", "Previous", "Next"),
    PicsLang("Pics"),
    DropLang("Drop", "Drag files here", "Sync", "Delete"),
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
      "Submit",
      "Sign Out"
    ),
    ProfileLang("Pics - Profile")
  )
