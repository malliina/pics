package com.malliina.pics.http4s

import Socials.log
import cats.effect.IO
import com.malliina.http.HttpClient
import com.malliina.pics.auth.*
import com.malliina.pics.http4s.Socials.cognitoAuthConf
import com.malliina.util.AppLogger
import com.malliina.web.*
import com.malliina.web.IdentityProvider.LoginWithAmazon
import Social.SocialConf

import java.time.Instant

object Socials:
  private val log = AppLogger(getClass)

  def cognitoAuthConf(authConf: AuthConf, httpClient: HttpClient[IO]): GenericAuthConf =
    new GenericAuthConf:
      override def conf = authConf
      override def http = httpClient

class Socials(conf: SocialConf, http: HttpClient[IO]):
  def cognitoValidator(identityProvider: IdentityProvider): CognitoAuthFlow = CognitoAuthFlow(
    "pics.auth.eu-west-1.amazoncognito.com",
    identityProvider,
    Validators.picsId,
    cognitoAuthConf(conf.amazonConf, http)
  )
  val twitter = TwitterAuthFlow(conf.twitterConf, http)
  val google = GoogleAuthFlow(conf.googleConf, http)
  val microsoft = MicrosoftAuthFlow(conf.microsoftConf, http)
  val github = GitHubAuthFlow(conf.githubConf, http)
  val amazon = cognitoValidator(LoginWithAmazon)
  val facebook = FacebookAuthFlow(conf.facebookConf, http)
  val appleToken =
    if conf.apple.enabled then SignInWithApple(conf.apple).signInWithAppleToken(Instant.now())
    else
      log.info("Sign in with Apple is disabled.")
      ClientSecret("disabled")
  val appleAuthConf = AuthConf(conf.apple.clientId, appleToken)
  val apple = AppleAuthFlow(appleAuthConf, AppleTokenValidator(Seq(conf.apple.clientId)), http)
