package controllers

import akka.stream.Materializer
import com.malliina.play.controllers.OAuthControl
import play.api.mvc.{Call, RequestHeader}

class Admin(val mat: Materializer) extends OAuthControl(mat) {
  override def isAuthorized(email: String): Boolean =
    email == "malliina123@gmail.com"

  // temp hack
  override def redirURL(request: RequestHeader): String =
    oAuthRedir.absoluteURL(secure = true)(request)

  override def startOAuth: Call = routes.Admin.initiate()

  override def oAuthRedir: Call = routes.Admin.redirResponse()

  override def onOAuthSuccess: Call = routes.Home.list()

  override def ejectCall: Call = routes.Home.eject()
}
