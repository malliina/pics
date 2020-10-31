package com.malliina.pics.http4s

import com.malliina.http.OkClient
import com.malliina.play.auth.{
  AuthConf,
  AuthError,
  AuthHandler,
  GoogleCodeValidator,
  GoogleValidator,
  OAuthConf,
  TwitterValidator
}
import com.malliina.values.Email
import controllers.Social.SocialConf
import play.api.mvc.{Call, RequestHeader, Results}

object Socials {
  def apply(conf: SocialConf, http: OkClient): Socials = new Socials(conf, http)
}

class Socials(conf: SocialConf, http: OkClient) {
  val handler = new AuthHandler {
    override def onAuthenticated(user: Email, req: RequestHeader) = Results.Ok

    override def onUnauthorized(error: AuthError, req: RequestHeader) = Results.Unauthorized
  }
  val dummyCall = Call("GET", "/")
  def oauthConf(auth: AuthConf) = OAuthConf(dummyCall, handler, auth, http)
  val twitter = TwitterValidator(oauthConf(conf.twitterConf))
  val google = GoogleCodeValidator(oauthConf(conf.googleConf))
}
