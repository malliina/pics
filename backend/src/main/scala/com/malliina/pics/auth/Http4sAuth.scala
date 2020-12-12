package com.malliina.pics.auth

import cats.effect.{ContextShift, IO}
import com.malliina.http.OkClient
import com.malliina.pics.http4s.PicsImplicits._
import com.malliina.pics.http4s.PicsService.{noCache, version10}
import com.malliina.pics.http4s.{PicsService, Reverse}
import com.malliina.pics.{AppConf, Errors, PicRequest2}
import com.malliina.play.auth.Validators
import com.malliina.values.{AccessToken, IdToken, Username}
import com.malliina.web.{CognitoAccessValidator, CognitoIdValidator}
import controllers.Social
import org.http4s.Credentials.Token
import org.http4s.headers.{Authorization, Cookie, Location}
import org.http4s.{Headers, MediaType, Response, ResponseCookie}
import play.api.libs.json.{Json, OWrites, Reads, Writes}

import scala.concurrent.duration.DurationInt

object Http4sAuth {
  def apply(conf: AppConf, cs: ContextShift[IO]): Http4sAuth =
    new Http4sAuth(
      JWT(conf.secret),
      Validators.picsAccess,
      Validators.picsId,
      Validators.google(OkClient.default),
      CookieConf("picsUser", "picsState", "picsReturnUri", "picsLastId")
    )(cs)

  case class TwitterState(requestToken: AccessToken)

  object TwitterState {
    implicit val json = Json.format[TwitterState]
  }
}

// Contextshift required as long as we use Future-based GoogleTokenAuth.validate
class Http4sAuth(
  val webJwt: JWT,
  ios: CognitoAccessValidator,
  android: CognitoIdValidator,
  google: GoogleTokenAuth,
  conf: CookieConf
)(implicit cs: ContextShift[IO]) {
  val returnUriKey = conf.returnUriKey
  val lastIdKey = conf.lastIdKey
  val cookiePath = Option("/")

  def authenticate(headers: Headers): IO[Either[IO[Response[IO]], PicRequest2]] = {
    val rs = PicsService.ranges(headers)
    if (rs.exists(_.satisfies(MediaType.text.html))) {
      IO.pure(web(headers))
    } else if (rs.exists(m => m.satisfies(version10) || m.satisfies(MediaType.application.json))) {
      jwt(headers)
    } else {
      IO.pure(Left(NotAcceptable(Json.toJson(Errors.single("Not acceptable.")), noCache)))
    }
  }

  def authenticateAll(headers: Headers): IO[Either[IO[Response[IO]], PicRequest2]] = {
    IO.pure(web(headers)).flatMap { e =>
      e.fold(_ => jwt(headers), user => IO.pure(Right(user)))
    }
  }

  private def web(headers: Headers): Either[IO[Response[IO]], PicRequest2] = user(headers).fold(
    {
      case MissingCredentials2(_, headers)
          if !Cookie.from(headers).exists(_.values.exists(_.name == Social.ProviderCookie)) =>
        Right(PicRequest2.anon(headers))
      case _ => Left(onUnauthorized(headers))
    },
    user => Right(PicRequest2.forUser(user, headers))
  )

  private def jwt(headers: Headers) =
    readJwt(headers)
      .map { io =>
        io.map { e =>
          e.fold(
            _ => Left(onUnauthorized(headers)),
            user => Right(PicRequest2.forUser(user.username, headers))
          )
        }
      }
      .fold(_ => IO.pure(Right(PicRequest2.anon(headers))), identity)

  private def readJwt(headers: Headers) = token(headers)
    .map { token =>
      IO(ios.validate(AccessToken(token.value)).orElse(android.validate(token)))
        .flatMap { e =>
          e.fold(
            err =>
              IO.fromFuture(IO(google.validate(token))).map { e =>
                e.map { email => EmailUser(email) }
              },
            user => IO.pure(Right(user))
          )
        }
        .map { e =>
          e.left.map { error =>
            JWTError2(error, headers)
          }
        }
    }
//    .fold(e => IO.pure(Right(PicRequest2.anon(headers))), identity)

  def token(headers: Headers) = headers
    .get(Authorization)
    .toRight(MissingCredentials2("Missing Authorization header", headers))
    .flatMap(_.credentials match {
      case Token(_, token) => Right(IdToken(token))
      case _               => Left(MissingCredentials2("Missing token.", headers))
    })

  def session[T: Reads](from: Headers): Either[IdentityError2, T] = read[T](conf.sessionKey, from)

  def withSession[T: OWrites](t: T, res: Response[IO]): res.Self =
    withJwt(conf.sessionKey, t, res)

  def clearSession(res: Response[IO]): res.Self =
    res
      .removeCookie(ResponseCookie(conf.sessionKey, "", path = cookiePath))
      .removeCookie(ResponseCookie(conf.userKey, "", path = cookiePath))

  private def user(headers: Headers): Either[IdentityError2, Username] =
    readUser(conf.userKey, headers)

  def withUser[T: Writes](t: T, res: Response[IO]): res.Self =
    withJwt(conf.userKey, t, res)

  def withJwt[T: Writes](cookieName: String, t: T, res: Response[IO]): res.Self = {
    val signed = webJwt.sign(t, 12.hours)
    res.addCookie(ResponseCookie(cookieName, signed.value, httpOnly = true, path = cookiePath))
  }

  private def readUser(cookieName: String, headers: Headers): Either[IdentityError2, Username] =
    read[UserPayload](cookieName, headers).map(_.username)

  private def read[T: Reads](cookieName: String, headers: Headers): Either[IdentityError2, T] =
    for {
      header <- Cookie.from(headers).toRight(MissingCredentials2("Cookie parsing error.", headers))
      cookie <- header.values
        .find(_.name == cookieName)
        .map(c => IdToken(c.content))
        .toRight(MissingCredentials2(s"Cookie not found: '$cookieName'.", headers))
      t <- webJwt.verify[T](cookie).left.map { err =>
        JWTError2(err, headers)
      }
    } yield t

  private def onUnauthorized(headers: Headers) =
    SeeOther(Location(Reverse.signIn))
}
