package com.malliina.pics.http4s

import com.malliina.http.OkClient
import com.malliina.pics.auth.{AppleCodeValidator, AppleTokenValidator}
import com.malliina.play.auth.CognitoCodeValidator.IdentityProvider
import com.malliina.play.auth.CognitoCodeValidator.IdentityProvider.LoginWithAmazon
import com.malliina.play.auth.{
  AuthConf,
  AuthError,
  AuthHandler,
  AuthResults,
  CognitoCodeValidator,
  CognitoUser,
  FacebookCodeValidator,
  GitHubCodeValidator,
  GoogleCodeValidator,
  GoogleValidator,
  MicrosoftCodeValidator,
  MicrosoftValidator,
  OAuthConf,
  TwitterValidator,
  Validators
}
import com.malliina.values.Email
import controllers.Social.{SessionKey, SocialConf}
import play.api.mvc.{Call, RequestHeader, Result, Results}

object Socials {
  def apply(conf: SocialConf, http: OkClient): Socials = new Socials(conf, http)
}

class Socials(conf: SocialConf, http: OkClient) {
  val handler: AuthHandler = new AuthHandler {
    override def onAuthenticated(user: Email, req: RequestHeader): Result = Results.Ok
    override def onUnauthorized(error: AuthError, req: RequestHeader): Result = Results.Unauthorized
  }
  object cognitoHandler extends AuthResults[CognitoUser] {
    override def onAuthenticated(user: CognitoUser, req: RequestHeader): Result =
      Results.Ok
    override def onUnauthorized(error: AuthError, req: RequestHeader): Result =
      handler.onUnauthorized(error, req)
  }
  def cognitoValidator(identityProvider: IdentityProvider) = CognitoCodeValidator(
    "pics.auth.eu-west-1.amazoncognito.com",
    identityProvider,
    Validators.picsId,
    picsAuthConf(conf.amazonConf, cognitoHandler)
  )
  val dummyCall = Call("GET", "/")
  val twitter = TwitterValidator(oauthConf(conf.twitterConf))
  val google = GoogleCodeValidator(oauthConf(conf.googleConf))
  val microsoft = MicrosoftCodeValidator(oauthConf(conf.microsoftConf))
  val github = GitHubCodeValidator(oauthConf(conf.githubConf))
  val amazon = cognitoValidator(LoginWithAmazon)
  val facebook = FacebookCodeValidator(oauthConf(conf.facebookConf))
  val apple =
    AppleCodeValidator(oauthConf(conf.apple), AppleTokenValidator(Seq(conf.apple.clientId)))
  def oauthConf[T](auth: AuthConf) = picsAuthConf(auth, handler)
  def picsAuthConf[T](auth: AuthConf, handler: AuthResults[T]) =
    OAuthConf(dummyCall, handler, auth, http)
}
