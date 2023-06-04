package com.malliina.pics.http4s

import cats.effect.Sync
import com.malliina.http.HttpClient
import com.malliina.pics.auth.*
import com.malliina.pics.auth.Social.SocialConf
import com.malliina.pics.http4s.Socials.{cognitoAuthConf, log}
import com.malliina.util.AppLogger
import com.malliina.web.*
import com.malliina.web.IdentityProvider.LoginWithAmazon

import java.time.Instant

object Socials:
  private val log = AppLogger(getClass)

  def cognitoAuthConf[F[_]](authConf: AuthConf, httpClient: HttpClient[F]): GenericAuthConf[F] =
    new GenericAuthConf[F]:
      override def conf = authConf
      override def http = httpClient

class Socials[F[_]: Sync](conf: SocialConf, http: HttpClient[F]):
  private def cognitoValidator(identityProvider: IdentityProvider): CognitoAuthFlow[F] =
    CognitoAuthFlow(
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
  private val appleToken =
    if conf.apple.enabled then SignInWithApple(conf.apple).signInWithAppleToken(Instant.now())
    else
      log.info("Sign in with Apple is disabled.")
      ClientSecret("disabled")
  private val appleAuthConf = AuthConf(conf.apple.clientId, appleToken)
  val apple = AppleAuthFlow(appleAuthConf, AppleTokenValidator(Seq(conf.apple.clientId)), http)
