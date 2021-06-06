package com.malliina.pics.auth

import _root_.play.api.libs.json.{Json, OWrites, Reads, Writes}
import cats.effect.IO
import com.malliina.http.io.HttpClientIO
import com.malliina.pics.auth.CredentialsResult.{AccessTokenResult, IdTokenResult, NoCredentials}
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.http4s.PicsImplicits._
import com.malliina.pics.http4s.PicsService.{noCache, version10}
import com.malliina.pics.http4s.{PicsService, Reverse}
import com.malliina.pics.{AppConf, Errors, PicRequest}
import com.malliina.play.auth.{JWTUsers, Validators}
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, ErrorMessage, IdToken, Username}
import com.malliina.web.{CognitoAccessValidator, CognitoIdValidator, JWTUser, OAuthError}
import controllers.Social.AuthProvider
import org.http4s.Credentials.Token
import org.http4s._
import org.http4s.headers.{Authorization, Cookie, Location}
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.duration.DurationInt

object Http4sAuth {
  private val log = AppLogger(getClass)

  def apply(conf: AppConf, db: PicsDatabase[IO]): Http4sAuth =
    new Http4sAuth(
      JWT(conf.secret),
      Validators.picsAccess,
      Validators.picsId,
      Validators.google(HttpClientIO()),
      db,
      CookieConf.pics
    )

  case class TwitterState(requestToken: AccessToken)

  object TwitterState {
    implicit val json = Json.format[TwitterState]
  }
}

class Http4sAuth(
  val webJwt: JWT,
  ios: CognitoAccessValidator,
  android: CognitoIdValidator,
  google: GoogleTokenAuth,
  db: PicsDatabase[IO],
  val cookieNames: CookieConf
) {
  val cookiePath = Option("/")

  def authenticate(headers: Headers): IO[Either[IO[Response[IO]], PicRequest]] = {
    val rs = PicsService.ranges(headers)
    if (rs.exists(r => r.satisfiedBy(MediaType.text.html))) {
      println("HTML headers")
      IO.pure(web(headers))
    } else if (
      rs.exists(m => m.satisfiedBy(version10) || m.satisfiedBy(MediaType.application.json))
    ) {
      println("JWT headers")
      jwt(headers)
    } else {
      println("No headers")
      IO.pure(Left(NotAcceptable(Json.toJson(Errors.single("Not acceptable.")), noCache)))
    }
  }

  /** Performs authentication disregarding the Accept header; tries opportunistically cookie-based auth first,
    * falling back to Bearer token auth should cookie auth fail.
    *
    * @param headers request headers
    */
  def authenticateAll(headers: Headers): IO[Either[IO[Response[IO]], PicRequest]] = {
    val userAuth: IO[Either[IdentityError, Username]] = IO.pure(user(headers)).flatMap { e =>
      e.fold(
        _ => readJwt(headers).fold(l => IO.pure(Left(l)), r => r.map(_.map(_.username))),
        user => IO.pure(Right(user))
      )
    }
    userAuth.map { e =>
      e.fold(
        {
          case MissingCredentials(_, headers)
              if !Cookie.from(headers).exists(_.values.exists(_.name == cookieNames.provider)) =>
            Right(PicRequest.anon(headers))
          case _ => Left(onUnauthorized(headers))
        },
        user => Right(PicRequest.forUser(user, headers))
      )
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

  private def readJwt(headers: Headers): Either[IdentityError, IO[Either[TokenError, JWTUser]]] =
    token(headers) match {
      case AccessTokenResult(token) =>
        val res = db.userByToken(token).map { opt =>
          opt
            .map { u => JWTUsers.user(u) }
            .toRight(TokenError(OAuthError(ErrorMessage("Invalid access token.")), headers))
        }
        Right(res)
      case IdTokenResult(token) =>
        val res = IO(ios.validate(AccessToken(token.value)).orElse(android.validate(token)))
          .flatMap { e =>
            e.fold(
              err =>
                google.validate(token).map { e =>
                  e.map { email => EmailUser(email) }
                },
              user => IO.pure(Right(user))
            )
          }
          .map { e =>
            e.left.map { error =>
              TokenError(error, headers)
            }
          }
        Right(res)
      case NoCredentials(headers) => Left(MissingCredentials("Creds required.", headers))
    }

  def token(headers: Headers): CredentialsResult = headers
    .get(Authorization)
    .fold[CredentialsResult](NoCredentials(headers)) { h =>
      h.credentials match {
        case Token(scheme, token) =>
          println(s"Token scheme $scheme token $token")
          if (scheme == CaseInsensitiveString("token")) AccessTokenResult(AccessToken(token))
          else IdTokenResult(IdToken(token))
        case _ =>
          NoCredentials(headers)
      }
    }

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
        TokenError(err, headers)
      }
    } yield t

  private def onUnauthorized(headers: Headers) =
    SeeOther(Location(Reverse.signIn))
}
