package com.malliina.pics.http4s

import cats.effect.IO
import com.malliina.http.HttpClient
import com.malliina.pics.auth.{AppleAuthFlow, AppleTokenValidator}
import com.malliina.pics.http4s.Socials.cognitoAuthConf
import com.malliina.play.auth._
import com.malliina.web.IdentityProvider.LoginWithAmazon
import com.malliina.web._
import controllers.Social.SocialConf

object Socials {
  def apply(conf: SocialConf, http: HttpClient[IO]): Socials = new Socials(conf, http)

  def cognitoAuthConf(authConf: AuthConf, httpClient: HttpClient[IO]): GenericAuthConf =
    new GenericAuthConf {
      override def conf = authConf
      override def http = httpClient
    }
}

class Socials(conf: SocialConf, http: HttpClient[IO]) {
  def cognitoValidator(identityProvider: IdentityProvider) = new CognitoAuthFlow(
    "pics.auth.eu-west-1.amazoncognito.com",
    identityProvider,
    Validators.picsId,
    cognitoAuthConf(conf.amazonConf, http)
  )
  val twitter = new TwitterAuthFlow(conf.twitterConf, http)
  val google = GoogleAuthFlow(conf.googleConf, http)
  val microsoft = MicrosoftAuthFlow(conf.microsoftConf, http)
  val github = new GitHubAuthFlow(conf.githubConf, http)
  val amazon = cognitoValidator(LoginWithAmazon)
  val facebook = new FacebookAuthFlow(conf.facebookConf, http)
  val apple = AppleAuthFlow(conf.apple, AppleTokenValidator(Seq(conf.apple.clientId)), http)
}
