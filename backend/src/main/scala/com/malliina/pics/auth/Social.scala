package com.malliina.pics.auth

import com.malliina.web.{AuthConf, ClientId, ClientSecret}

import scala.concurrent.duration.{Duration, DurationInt}

object Social:
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
    apple: Option[SignInWithApple.Conf]
  )

  def google(secret: ClientSecret) = AuthConf(
    ClientId("122390040180-78dau8o0fd6eelgfdhed6g2pj4hlh701.apps.googleusercontent.com"),
    secret
  )

  def github(secret: ClientSecret) = AuthConf(
    ClientId("9f6720de7a1c2b04a9b2"),
    secret
  )

  def microsoft(secret: ClientSecret) = AuthConf(
    ClientId("cafee8ae-14be-49e1-9ef4-3152e1412799"),
    secret
  )

  def facebook(secret: ClientSecret) = AuthConf(
    ClientId("2044590915867453"),
    secret
  )

  def twitter(secret: ClientSecret) = AuthConf(
    ClientId("nFdM58PDsZoCrVmyEsPmwmqqB"),
    secret
  )

  def amazon(secret: ClientSecret) = AuthConf(
    ClientId("2rnqepv44epargdosba6nlg2t9"),
    secret
  )

sealed abstract class AuthProvider(val name: String)

object AuthProvider:
  def forString(s: String): Either[String, AuthProvider] =
    Seq(Google, Microsoft, Amazon, Twitter, Facebook, GitHub, Apple)
      .find(_.name == s)
      .toRight(s"Unknown auth provider: '$s'.")

  def unapply(str: String): Option[AuthProvider] =
    forString(str).toOption

  case object Google extends AuthProvider("google")
  case object Microsoft extends AuthProvider("microsoft")
  case object Amazon extends AuthProvider("amazon")
  case object Twitter extends AuthProvider("twitter")
  case object Facebook extends AuthProvider("facebook")
  case object GitHub extends AuthProvider("github")
  case object Apple extends AuthProvider("apple")
