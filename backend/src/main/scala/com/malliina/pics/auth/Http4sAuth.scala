package com.malliina.pics.auth

import cats.effect.{ContextShift, IO}
import com.malliina.http.OkClient
import com.malliina.pics.http4s.PicsImplicits._
import com.malliina.pics.http4s.PicsService.{noCache, version10}
import com.malliina.pics.http4s.{PicsService, Reverse}
import com.malliina.pics.{AppConf, Errors, PicRequest}
import com.malliina.play.auth.Validators
import com.malliina.values.{AccessToken, IdToken, Username}
import com.malliina.web.{CognitoAccessValidator, CognitoIdValidator}
import controllers.Social.AuthProvider
import org.http4s.Credentials.Token
import org.http4s.headers.{Authorization, Cookie, Location}
import org.http4s._
import _root_.play.api.libs.json.{Json, OWrites, Reads, Writes}

import scala.concurrent.duration.DurationInt

object Http4sAuth {
  def apply(conf: AppConf, cs: ContextShift[IO]): Http4sAuth =
    new Http4sAuth(
      JWT(conf.secret),
      Validators.picsAccess,
      Validators.picsId,
      Validators.google(OkClient.default),
      CookieConf.pics
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
  val cookieNames: CookieConf
)(implicit cs: ContextShift[IO]) {
  val cookiePath = Option("/")

  def authenticate(headers: Headers): IO[Either[IO[Response[IO]], PicRequest]] = {
    val rs = PicsService.ranges(headers)
    if (rs.exists(_.satisfies(MediaType.text.html))) {
      IO.pure(web(headers))
    } else if (rs.exists(m => m.satisfies(version10) || m.satisfies(MediaType.application.json))) {
      jwt(headers)
    } else {
      IO.pure(Left(NotAcceptable(Json.toJson(Errors.single("Not acceptable.")), noCache)))
    }
  }

  def authenticateAll(headers: Headers): IO[Either[IO[Response[IO]], PicRequest]] = {
    IO.pure(web(headers)).flatMap { e =>
      e.fold(_ => jwt(headers), user => IO.pure(Right(user)))
    }
  }

  private def web(headers: Headers): Either[IO[Response[IO]], PicRequest] = user(headers).fold(
    {
      case MissingCredentials(_, headers)
          if !Cookie.from(headers).exists(_.values.exists(_.name == cookieNames.provider)) =>
        Right(PicRequest.anon(headers))
      case _ => Left(onUnauthorized(headers))
    },
    user => Right(PicRequest.forUser(user, headers))
  )

  private def jwt(headers: Headers) =
    readJwt(headers)
      .map { io =>
        io.map { e =>
          e.fold(
            _ => Left(onUnauthorized(headers)),
            user => Right(PicRequest.forUser(user.username, headers))
          )
        }
      }
      .fold(_ => IO.pure(Right(PicRequest.anon(headers))), identity)

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
            JWTError(error, headers)
          }
        }
    }

  def token(headers: Headers) = headers
    .get(Authorization)
    .toRight(MissingCredentials("Missing Authorization header", headers))
    .flatMap(_.credentials match {
      case Token(_, token) => Right(IdToken(token))
      case _               => Left(MissingCredentials("Missing token.", headers))
    })

  def session[T: Reads](from: Headers): Either[IdentityError, T] =
    read[T](cookieNames.session, from)

  def withSession[T: OWrites](t: T, isSecure: Boolean, res: Response[IO]): res.Self =
    withJwt(cookieNames.session, t, isSecure, res)

  def clearSession(res: Response[IO]): res.Self =
    res
      .removeCookie(ResponseCookie(cookieNames.session, "", path = cookiePath))
      .removeCookie(ResponseCookie(cookieNames.user, "", path = cookiePath))

  private def user(headers: Headers): Either[IdentityError, Username] =
    readUser(cookieNames.user, headers)

  def withPicsUser(
    user: UserPayload,
    isSecure: Boolean,
    provider: AuthProvider,
    res: Response[IO]
  ) =
    withUser(user, isSecure, res)
      .removeCookie(cookieNames.returnUri)
      .addCookie(responseCookie(cookieNames.lastId, user.username.name))
      .addCookie(responseCookie(cookieNames.provider, provider.name))

  def withUser[T: Writes](t: T, isSecure: Boolean, res: Response[IO]): res.Self =
    withJwt(cookieNames.user, t, isSecure, res)

  def withJwt[T: Writes](
    cookieName: String,
    t: T,
    isSecure: Boolean,
    res: Response[IO]
  ): res.Self = {
    val signed = webJwt.sign(t, 12.hours)
    res.addCookie(
      ResponseCookie(
        cookieName,
        signed.value,
        httpOnly = true,
        secure = isSecure,
        path = cookiePath
      )
    )
  }

  def responseCookie(name: String, value: String) = ResponseCookie(
    name,
    value,
    Option(HttpDate.MaxValue),
    path = cookiePath,
    secure = true,
    httpOnly = true
  )

  private def readUser(cookieName: String, headers: Headers): Either[IdentityError, Username] =
    read[UserPayload](cookieName, headers).map(_.username)

  private def read[T: Reads](cookieName: String, headers: Headers): Either[IdentityError, T] =
    for {
      header <- Cookie.from(headers).toRight(MissingCredentials("Cookie parsing error.", headers))
      cookie <- header.values
        .find(_.name == cookieName)
        .map(c => IdToken(c.content))
        .toRight(MissingCredentials(s"Cookie not found: '$cookieName'.", headers))
      t <- webJwt.verify[T](cookie).left.map { err =>
        JWTError(err, headers)
      }
    } yield t

  private def onUnauthorized(headers: Headers) =
    SeeOther(Location(Reverse.signIn))
}
