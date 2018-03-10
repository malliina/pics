package controllers

import com.malliina.http.AsyncHttp
import com.malliina.oauth.{GoogleOAuth, GoogleOAuthCredentials}
import com.malliina.pics.PicOwner
import com.malliina.pics.html.PicsHtml
import com.malliina.play.controllers.OAuthControl
import com.malliina.play.http.FullUrls
import com.malliina.play.models.Email
import controllers.Admin.log
import play.api.Logger
import play.api.mvc.Results.Redirect
import play.api.mvc._

object Admin {
  private val log = Logger(getClass)

  val AdminEmail = Email("malliina123@gmail.com")
  val AdminUser = PicOwner("malliina123@gmail.com")
}

class Admin(html: PicsHtml, creds: GoogleOAuthCredentials, actions: ActionBuilder[Request, AnyContent])
  extends OAuthControl(actions, creds) {
  val reverse = routes.Admin
  val httpClient = new AsyncHttp()

  override def isAuthorized(email: Email): Boolean = email == Admin.AdminEmail

  override def startOAuth: Call = reverse.initiate()

  override def oAuthRedir: Call = reverse.redirResponse()

  override def onOAuthSuccess: Call = routes.PicsController.list()

  override def ejectCall: Call = routes.PicsController.list()

  def logout = actions {
    ejectWith(logoutMessage).withNewSession
  }

  def ejectUser = actions { (req: Request[AnyContent]) =>
    Results.Ok(html.eject(req.flash.get(messageKey)))
  }

  override def onOAuthUnauthorized(email: Email) = {
    Redirect(reverse.ejectUser()).flashing(messageKey -> unauthorizedMessage(email))
  }
}
