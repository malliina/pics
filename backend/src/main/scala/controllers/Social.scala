package controllers

import com.malliina.web.AuthConf

import scala.concurrent.duration.{Duration, DurationInt}

object Social {
  val PromptKey = "prompt"
  val LoginCookieDuration: Duration = 3650.days
  val SelectAccount = "select_account"

  val LastIdCookie = "picsLastId"

  case class SocialConf(
    githubConf: AuthConf,
    microsoftConf: AuthConf,
    googleConf: AuthConf,
    facebookConf: AuthConf,
    twitterConf: AuthConf,
    amazonConf: AuthConf,
    apple: AuthConf
  )

  sealed abstract class AuthProvider(val name: String)

  object AuthProvider {
    def forString(s: String): Either[String, AuthProvider] =
      Seq(Google, Microsoft, Amazon, Twitter, Facebook, GitHub, Apple)
        .find(_.name == s)
        .toRight(s"Unknown auth provider: '$s'.")

    def unapply(str: String): Option[AuthProvider] =
      forString(str).toOption
  }

  case object Google extends AuthProvider("google")
  case object Microsoft extends AuthProvider("microsoft")
  case object Amazon extends AuthProvider("amazon")
  case object Twitter extends AuthProvider("twitter")
  case object Facebook extends AuthProvider("facebook")
  case object GitHub extends AuthProvider("github")
  case object Apple extends AuthProvider("apple")
}
