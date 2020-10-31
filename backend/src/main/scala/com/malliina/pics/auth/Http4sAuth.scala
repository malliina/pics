package com.malliina.pics.auth

import com.malliina.pics.http4s.Reverse
import com.malliina.pics.{AppConf, PicRequest2}
import com.malliina.values.{IdToken, Username}
import controllers.Social
import org.http4s.Headers
import org.http4s.dsl.io._
import org.http4s.headers.{Cookie, Location}

object Http4sAuth {
  def apply(conf: AppConf): Http4sAuth = new Http4sAuth(JsonJWS(conf.secret))
}

class Http4sAuth(jwt: JsonJWS) {
  def web(headers: Headers) = authCookie(headers).fold(
    {
      case MissingCredentials2(_, headers) => Right(PicRequest2.anon(headers))
      case _                               => Left(onUnauthorized(headers))
    },
    user => Right(PicRequest2.forUser(user, headers))
  )

  def authCookie(headers: Headers) = cookie(Social.SessionKey, headers)

  def cookie(cookieName: String, headers: Headers): Either[IdentityError2, Username] = for {
    header <- Cookie.from(headers).toRight(MissingCredentials2("Cookie parsing error.", headers))
    cookie <- header.values
      .find(_.name == cookieName)
      .toRight(MissingCredentials2(s"Cookie not found: '$cookieName'.", headers))
    token <- jwt.verify[UserPayload](IdToken(cookie.content)).left.map { err =>
      JWTError2(err, headers)
    }
  } yield token.username

  def onUnauthorized(headers: Headers) = SeeOther(Location(Reverse.signIn))
}
