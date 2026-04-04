package com.malliina.pics.auth

import cats.effect.IO
import com.malliina.pics.PicUsername
import com.malliina.values.{Email, Username}
import com.malliina.web.{CognitoUser, CognitoUserId, JWTUser}
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.Request
import cats.syntax.all.toFunctorOps

import scala.concurrent.Future

trait EmailAuth:

  /** Fails with [[IdentityException]] if authentication fails.
    *
    * @return
    *   the authenticated user's email address
    */
  def authEmail(rh: Request[IO]): Future[Email]

case class EmailUser(email: Email) extends JWTUser:
  override val username = Username.unsafe(email.email)

sealed abstract class UserSubject

object UserSubject:
  given Encoder[UserSubject] =
    case s @ SocialEmail(_) => s.asJson
    case c @ Cognito(_)     => c.asJson
    case u @ SpecialUser(_) => u.asJson
  given Decoder[UserSubject] =
    Decoder[SocialEmail].widen.or(Decoder[Cognito].widen).or(Decoder[SpecialUser].widen)

case class SocialEmail(email: Email) extends UserSubject derives Codec.AsObject
case class Cognito(cognito: CognitoUserId) extends UserSubject derives Codec.AsObject
case class SpecialUser(username: PicUsername) extends UserSubject derives Codec.AsObject
object SpecialUser:
  val anon = SpecialUser(PicUsername.anon)

case class UserPayload(
  username: PicUsername,
  subject: UserSubject
) derives Codec.AsObject:
  def email: Option[Email] = subject match
    case SocialEmail(email) => Option(email)
    case _                  => None
  def cognito: Option[CognitoUserId] = subject match
    case Cognito(id) => Option(id)
    case _           => None
  def describe: String =
    val emailStr = email.fold("")(e => s" (email $e)")
    val cognitoStr = cognito.fold("")(id => s" (id $id)")
    s"$username$emailStr$cognitoStr"
  override def toString = describe

object UserPayload:
  val anon = user(PicUsername.anon)

  private def user(name: PicUsername): UserPayload =
    apply(name, SpecialUser(name))

  def email(email: Email): UserPayload =
    apply(PicUsername.fromEmail(email), SocialEmail(email))

  def cognito(user: CognitoUser): UserPayload =
    apply(PicUsername.fromUser(user.username), Cognito(user.sub))
