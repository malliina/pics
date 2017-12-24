package controllers

import com.malliina.oauth.GoogleOAuthCredentials
import com.malliina.pics.html.PicsHtml
import com.malliina.play.controllers.OAuthControl
import com.malliina.play.models.Email
import play.api.mvc._

class Admin(html: PicsHtml, creds: GoogleOAuthCredentials, actions: ActionBuilder[Request, AnyContent])
  extends OAuthControl(actions, creds) {
  val authorizedEmail = Email("malliina123@gmail.com")
  val reverse = routes.Admin

  override def isAuthorized(email: Email): Boolean = email == authorizedEmail

  override def startOAuth: Call = reverse.initiate()

  override def oAuthRedir: Call = reverse.redirResponse()

  override def onOAuthSuccess: Call = routes.PicsController.list()

  override def ejectCall: Call = reverse.ejectUser()

  def logout = actions {
    ejectWith(logoutMessage).withNewSession
  }

  def ejectUser = actions { (req: Request[AnyContent]) =>
    Results.Ok(html.eject(req.flash.get(messageKey)))
  }
}
