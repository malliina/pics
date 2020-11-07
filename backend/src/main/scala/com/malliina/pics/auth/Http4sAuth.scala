package com.malliina.pics.auth

import cats.effect.IO
import com.malliina.pics.http4s.Reverse
import com.malliina.pics.{AppConf, PicRequest2}
import com.malliina.values.{IdToken, Username}
import org.http4s.dsl.io._
import org.http4s.headers.{Cookie, Location}
import org.http4s.{Headers, Response, ResponseCookie}
import play.api.libs.json.{Reads, Writes}

import scala.concurrent.duration.DurationInt

object Http4sAuth {
  def apply(conf: AppConf): Http4sAuth =
    new Http4sAuth(
      JWT(conf.secret),
      CookieConf("picsUser", "picsSession", "picsReturnUri", "picsLastId")
    )
}

class Http4sAuth(val jwt: JWT, conf: CookieConf) {
  val returnUriKey = conf.returnUriKey
  val lastIdKey = conf.lastIdKey

  def web(headers: Headers): Either[IO[Response[IO]], PicRequest2] = user(headers).fold(
    {
      case MissingCredentials2(_, headers) => Right(PicRequest2.anon(headers))
      case _                               => Left(onUnauthorized(headers))
    },
    user => Right(PicRequest2.forUser(user, headers))
  )

  def session[T: Reads](from: Headers): Either[IdentityError2, T] = read[T](conf.sessionKey, from)
  def withSession[T: Writes](t: T, res: Response[IO]): res.Self =
    withJwt(conf.sessionKey, t, res)

  def user(headers: Headers): Either[IdentityError2, Username] =
    readUser(conf.userKey, headers)

  def withUser[T: Writes](t: T, res: Response[IO]): res.Self =
    withJwt(conf.userKey, t, res)

  def withJwt[T: Writes](cookieName: String, t: T, res: Response[IO]): res.Self = {
    val signed = jwt.sign(t, 12.hours)
    res.addCookie(ResponseCookie(cookieName, signed.value))
  }

  private def readUser(cookieName: String, headers: Headers): Either[IdentityError2, Username] =
    read[UserPayload](cookieName, headers).map(_.username)

  def read[T: Reads](cookieName: String, headers: Headers): Either[IdentityError2, T] =
    for {
      header <- Cookie.from(headers).toRight(MissingCredentials2("Cookie parsing error.", headers))
      cookie <- header.values
        .find(_.name == cookieName)
        .map(c => IdToken(c.content))
        .toRight(MissingCredentials2(s"Cookie not found: '$cookieName'.", headers))
      t <- jwt.verify[T](cookie).left.map { err =>
        JWTError2(err, headers)
      }
    } yield t

  def onUnauthorized(headers: Headers) = SeeOther(Location(Reverse.signIn))
}
