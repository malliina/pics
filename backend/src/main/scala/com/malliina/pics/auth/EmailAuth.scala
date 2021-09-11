package com.malliina.pics.auth

import cats.effect.IO
import com.malliina.values.{Email, Username}
import com.malliina.web.JWTUser
import org.http4s.Request
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import scala.concurrent.Future

trait EmailAuth:

  /** Fails with [[IdentityException]] if authentication fails.
    *
    * @return
    *   the authenticated user's email address
    */
  def authEmail(rh: Request[IO]): Future[Email]

case class EmailUser(email: Email) extends JWTUser:
  override val username = Username(email.email)

case class UserPayload(username: Username)

object UserPayload:
  implicit val json: Codec[UserPayload] = deriveCodec[UserPayload]

  def email(email: Email): UserPayload = apply(Username(email.value))
