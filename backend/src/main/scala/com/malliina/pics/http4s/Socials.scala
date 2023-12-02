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
  private val appleConf = conf.apple
    .map: appleConf =>
      val token = SignInWithApple(appleConf).signInWithAppleToken(Instant.now())
      AuthConf(appleConf.clientId, token)
    .getOrElse:
      log.info("Sign in with Apple is disabled.")
      AuthConf(ClientId("unused"), ClientSecret("disabled"))
  val apple = AppleAuthFlow(appleConf, AppleTokenValidator(Seq(appleConf.clientId)), http)
