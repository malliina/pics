package com.malliina.pics

import com.malliina.values.StringEnumCompanion

enum Language(val code: String):
  case English extends Language("en-US")
  case Finnish extends Language("fi-FI")
  case Swedish extends Language("sv-SE")

object Language extends StringEnumCompanion[Language]:
  override def all: Seq[Language] = Seq(English, Swedish, Finnish)
  override def write(t: Language): String = t.code
  val default: Language = English

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

case class PicsLang(
  title: String,
  noPictures: String,
  makePrivate: String,
  makePublic: String,
  delete: String,
  copied: String
)
case class DropLang(
  title: String,
  dragFilesHere: String,
  sync: String,
  delete: String,
  keyPlaceholder: String
)
case class ProfileLang(title: String)

case class Lang(
  language: Language,
  nav: NavLang,
  pics: PicsLang,
  drop: DropLang,
  login: LoginLang,
  profile: ProfileLang
)

object Lang:
  val cookieName = "pics-lang"

  val en = Lang(
    Language.English,
    NavLang("Pics", "Navigation", "Previous", "Next"),
    PicsLang("Pics", "No pictures.", "Make private", "Make public", "Delete", "Copied!"),
    DropLang("Drop", "Drag files here", "Sync", "Delete", "key"),
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
  val se = Lang(
    Language.Swedish,
    NavLang("Bilder", "Navigering", "Föregående", "Följande"),
    PicsLang("Bilder", "Inga bilder.", "Gör privat", "Offentliggör", "Radera", "Kopierat!"),
    DropLang("Droppa", "Dra filer hit", "Synkronisera", "Radera", "nyckel"),
    LoginLang(
      "Bilder - Skapa konto",
      "Logga in",
      "Logga in med",
      "Skapa konto",
      "E-post",
      "Lösenord",
      "Glömt lösenord?",
      "Skicka kod",
      "Skicka kod på nytt",
      "Kod",
      "Bekräfta",
      "Nytt lösenord",
      "Rensa",
      "Skicka",
      "Logga ut"
    ),
    ProfileLang("Bilder - Profil")
  )

  def apply(language: Language): Lang = language match
    case Language.English => en
    case Language.Finnish => en
    case Language.Swedish => se
