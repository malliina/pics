package controllers

import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.play.controllers.OAuthControl
import com.malliina.play.models.Email
import play.api.mvc.{ActionBuilder, AnyContent, Call, Request}

class Admin(creds: GoogleOAuthCredentials, actions: ActionBuilder[Request, AnyContent])
  extends OAuthControl(actions, creds) {
  val authorizedEmail = Email("malliina123@gmail.com")

  override def isAuthorized(email: Email): Boolean = email == authorizedEmail

  override def startOAuth: Call = routes.Admin.initiate()

  override def oAuthRedir: Call = routes.Admin.redirResponse()

  override def onOAuthSuccess: Call = routes.Home.list()

  override def ejectCall: Call = routes.Home.eject()
}
