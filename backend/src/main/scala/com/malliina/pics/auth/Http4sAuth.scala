package com.malliina.pics.auth

import cats.effect.IO
import com.malliina.http.HttpClient
import com.malliina.http.io.HttpClientIO
import com.malliina.pics.auth.CredentialsResult.{AccessTokenResult, IdTokenResult, NoCredentials}
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.http4s.PicsImplicits.*
import com.malliina.pics.http4s.PicsService.version10
import com.malliina.pics.http4s.{BasicService, PicsService, Reverse, Urls}
import com.malliina.pics.{AppConf, Errors, PicRequest}
import com.malliina.pics.auth.JWTUsers
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, ErrorMessage, IdToken, Username}
import com.malliina.web.{CognitoAccessValidator, CognitoIdValidator, JWTUser, OAuthError}
import controllers.AuthProvider
import io.circe.*
import io.circe.syntax.EncoderOps
import io.circe.generic.semiauto.deriveCodec
import org.http4s.Credentials.Token
import org.http4s.*
import org.http4s.headers.{Authorization, Cookie, Location}
import org.typelevel.ci.CIStringSyntax

import scala.concurrent.duration.DurationInt

object Http4sAuth:
  private val log = AppLogger(getClass)

  def apply(conf: AppConf, db: PicsDatabase[IO], http: HttpClient[IO]): Http4sAuth =
    new Http4sAuth(
      JWT(conf.secret),
      Validators.picsAccess,
      Validators.picsId,
      Validators.google(http),
      db,
      CookieConf.pics
    )

  case class TwitterState(requestToken: AccessToken)

  object TwitterState:
    implicit val json: Codec[TwitterState] = deriveCodec[TwitterState]

class Http4sAuth(
  val webJwt: JWT,
  ios: CognitoAccessValidator,
  android: CognitoIdValidator,
  google: GoogleTokenAuth,
  db: PicsDatabase[IO],
  val cookieNames: CookieConf
):
  val cookiePath = Option("/")

  def authenticate(headers: Headers): IO[Either[IO[Response[IO]], PicRequest]] =
    val rs = PicsService.ranges(headers)
    if rs.exists(r => r.satisfiedBy(MediaType.text.html)) then IO.pure(web(headers))
    else if rs.exists(m =>
        m.satisfiedBy(version10) || m.satisfiedBy(MediaType.application.json)
      )
    then jwt(headers)
    else IO.pure(Left(NotAcceptable(Errors.single("Not acceptable.").asJson, BasicService.noCache)))

  /** Performs authentication disregarding the Accept header; tries opportunistically cookie-based
    * auth first, falling back to Bearer token auth should cookie auth fail.
    *
    * @param headers
    *   request headers
    */
  def authenticateAll(headers: Headers): IO[Either[IO[Response[IO]], PicRequest]] =
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
              if !headers.get[Cookie].exists(_.values.exists(_.name == cookieNames.provider)) =>
            Right(PicRequest.anon(headers))
          case _ => Left(onUnauthorized(headers))
        },
        user => Right(PicRequest.forUser(user, headers))
      )
    }

  private def web(headers: Headers): Either[IO[Response[IO]], PicRequest] = user(headers).fold(
    {
      case MissingCredentials(_, headers)
          if !headers.get[Cookie].exists(_.values.exists(_.name == cookieNames.provider)) =>
        Right(PicRequest.anon(headers))
      case _ => Left(onUnauthorized(headers))
    },
    user => Right(PicRequest.forUser(user, headers))
  )

  private def jwt(headers: Headers) =
    readJwt(headers).map { io =>
      io.map { e =>
        e.fold(
          _ => Left(onUnauthorized(headers)),
          user => Right(PicRequest.forUser(user.username, headers))
        )
      }
    }
      .fold(_ => IO.pure(Right(PicRequest.anon(headers))), identity)

  private def readJwt(headers: Headers): Either[IdentityError, IO[Either[TokenError, JWTUser]]] =
    token(headers) match
      case AccessTokenResult(token) =>
        val res = db.userByToken(token).map { opt =>
          opt.map { u => JWTUsers.user(u) }
            .toRight(TokenError(OAuthError(ErrorMessage("Invalid access token.")), headers))
        }
        Right(res)
      case IdTokenResult(token) =>
        val res =
          IO(ios.validate(AccessToken(token.value)).orElse(android.validate(token))).flatMap { e =>
            e.fold(
              err =>
                google.validate(token).map { e =>
                  e.map { email => EmailUser(email) }
                },
              user => IO.pure(Right(user))
            )
          }.map { e =>
            e.left.map { error =>
              TokenError(error, headers)
            }
          }
        Right(res)
      case NoCredentials(headers) => Left(MissingCredentials("Creds required.", headers))

  def token(headers: Headers): CredentialsResult = headers
    .get[Authorization]
    .fold[CredentialsResult](NoCredentials(headers)) { h =>
      h.credentials match
        case Token(scheme, token) =>
          if scheme == ci"token" then AccessTokenResult(AccessToken(token))
          else IdTokenResult(IdToken(token))
        case _ =>
          NoCredentials(headers)
    }

  def session[T: Decoder](from: Headers): Either[IdentityError, T] =
    read[T](cookieNames.session, from)

  def withSession[T: Encoder](t: T, req: Request[IO], res: Response[IO]): res.Self =
    withJwt(cookieNames.session, t, req, res)

  def clearSession(res: Response[IO]): res.Self =
    res
      .removeCookie(ResponseCookie(cookieNames.session, "", path = cookiePath))
      .removeCookie(ResponseCookie(cookieNames.user, "", path = cookiePath))

  private def user(headers: Headers): Either[IdentityError, Username] =
    readUser(cookieNames.user, headers)

  def withPicsUser(
    user: UserPayload,
    provider: AuthProvider,
    req: Request[IO],
    res: Response[IO]
  ) =
    withUser(user, req, res)
      .removeCookie(cookieNames.returnUri)
      .addCookie(responseCookie(cookieNames.lastId, user.username.name))
      .addCookie(responseCookie(cookieNames.provider, provider.name))

  def withUser[T: Encoder](t: T, req: Request[IO], res: Response[IO]): res.Self =
    withJwt(cookieNames.user, t, req, res)

  def withJwt[T: Encoder](
    cookieName: String,
    t: T,
    req: Request[IO],
    res: Response[IO]
  ): res.Self =
    val signed = webJwt.sign(t, 12.hours)
    val top = Urls.topDomainFrom(req)
    res.addCookie(
      ResponseCookie(
        cookieName,
        signed.value,
        httpOnly = true,
        secure = Urls.isSecure(req),
        path = cookiePath,
        domain = Option.when(top.nonEmpty)(top)
      )
    )

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

  private def read[T: Decoder](cookieName: String, headers: Headers): Either[IdentityError, T] =
    for
      header <- headers.get[Cookie].toRight(MissingCredentials("Cookie parsing error.", headers))
      cookie <- header.values
        .find(_.name == cookieName)
        .map(c => IdToken(c.content))
        .toRight(MissingCredentials(s"Cookie not found: '$cookieName'.", headers))
      t <- webJwt.verify[T](cookie).left.map { err =>
        TokenError(err, headers)
      }
    yield t

  private def onUnauthorized(headers: Headers) =
    SeeOther(Location(Reverse.signIn))
