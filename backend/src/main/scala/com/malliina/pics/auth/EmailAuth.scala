package com.malliina.pics.auth

import com.malliina.play.auth.JWTUser
import com.malliina.values.{Email, Username}
import play.api.libs.json.Json
import play.api.mvc.RequestHeader

import scala.concurrent.Future

trait EmailAuth {

  /** Fails with [[IdentityException]] if authentication fails.
    *
    * @return the authenticated user's email address
    */
  def authEmail(rh: RequestHeader): Future[Email]
}

case class EmailUser(email: Email) extends JWTUser {
  override val username = Username(email.email)
}

case class UserPayload(username: Username)

object UserPayload {
  implicit val json = Json.format[UserPayload]
}
