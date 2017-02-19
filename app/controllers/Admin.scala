package controllers

import akka.stream.Materializer
import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.play.controllers.OAuthControl
import play.api.mvc.Call

class Admin(creds: GoogleOAuthCredentials, val mat: Materializer) extends OAuthControl(creds, mat) {
  override def isAuthorized(email: String): Boolean =
    email == "malliina123@gmail.com"

  override def startOAuth: Call = routes.Admin.initiate()

  override def oAuthRedir: Call = routes.Admin.redirResponse()

  override def onOAuthSuccess: Call = routes.Home.list()

  override def ejectCall: Call = routes.Home.eject()
}
