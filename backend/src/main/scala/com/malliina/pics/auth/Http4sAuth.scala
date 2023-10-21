package com.malliina.pics.auth

import cats.effect.Sync
import cats.syntax.all.*
import com.malliina.http.HttpClient
import com.malliina.pics.auth.CredentialsResult.{AccessTokenResult, IdTokenResult, NoCredentials}
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.http4s.PicsService.version10
import com.malliina.pics.http4s.{BasicService, PicsService, Reverse, Urls}
import com.malliina.pics.{AppConf, Errors, PicRequest}
import com.malliina.values.{AccessToken, ErrorMessage, IdToken, Username}
import com.malliina.web.{CognitoAccessValidator, CognitoIdValidator, JWTUser, OAuthError}
import io.circe.*
import io.circe.syntax.EncoderOps
import io.circe.generic.semiauto.deriveCodec
import org.http4s.Credentials.Token
import org.http4s.*
import org.http4s.headers.{Authorization, Cookie, Location}
import org.typelevel.ci.CIStringSyntax

import scala.annotation.unused
import scala.concurrent.duration.DurationInt

object Http4sAuth:
  def default[F[_]: Sync](conf: AppConf, db: PicsDatabase[F], http: HttpClient[F]): Http4sAuth[F] =
    Http4sAuth(
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

class Http4sAuth[F[_]: Sync](
  val webJwt: JWT,
  ios: CognitoAccessValidator,
  android: CognitoIdValidator,
  google: GoogleTokenAuth[F],
  db: PicsDatabase[F],
  val cookieNames: CookieConf
) extends BasicService[F]:
  val cookiePath = Option("/")
  val F = Sync[F]

  def authenticate(headers: Headers): F[Either[F[Response[F]], PicRequest]] =
    val rs = PicsService.ranges(headers)
    if rs.exists(r => r.satisfiedBy(MediaType.text.html)) then F.pure(web(headers))
    else if rs.exists(m => m.satisfiedBy(version10) || m.satisfiedBy(MediaType.application.json))
    then jwt(headers)
    else F.pure(Left(NotAcceptable(Errors("Not acceptable.").asJson, noCache)))

  /** Performs authentication disregarding the Accept header; tries opportunistically cookie-based
    * auth first, falling back to Bearer token auth should cookie auth fail.
    *
    * @param headers
    *   request headers
    */
  def authenticateAll(headers: Headers): F[Either[F[Response[F]], PicRequest]] =
    val userAuth: F[Either[IdentityError, Username]] = F
      .pure(user(headers))
      .flatMap: e =>
        e.fold(
          _ => readJwt(headers).fold(l => F.pure(Left(l)), r => r.map(_.map(_.username))),
          user => F.pure(Right(user))
        )
    userAuth.map: e =>
      e.fold(
        {
          case MissingCredentials(_, headers)
              if !headers.get[Cookie].exists(_.values.exists(_.name == cookieNames.provider)) =>
            Right(PicRequest.anon(headers))
          case _ => Left(onUnauthorized(headers))
        },
        user => Right(PicRequest.forUser(user, headers))
      )

  private def web(headers: Headers): Either[F[Response[F]], PicRequest] = user(headers).fold(
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
      io.map: e =>
        e.fold(
          _ => Left(onUnauthorized(headers)),
          user => Right(PicRequest.forUser(user.username, headers))
        )
    }
      .fold(_ => F.pure(Right(PicRequest.anon(headers))), identity)

  private def readJwt(headers: Headers): Either[IdentityError, F[Either[TokenError, JWTUser]]] =
    token(headers) match
      case AccessTokenResult(token) =>
        val res = db
          .userByToken(token)
          .map: opt =>
            opt.map { u => JWTUsers.user(u) }
              .toRight(TokenError(OAuthError(ErrorMessage("Invalid access token.")), headers))
        Right(res)
      case IdTokenResult(token) =>
        val res: F[Either[TokenError, JWTUser]] =
          F.delay(ios.validate(AccessToken(token.value)).orElse(android.validate(token)))
            .flatMap: e =>
              e.fold(
                err =>
                  google
                    .validate(token)
                    .map: e =>
                      e.map { email => EmailUser(email) },
                user => F.pure(Right(user))
              )
            .map: e =>
              e.left.map: error =>
                TokenError(error, headers)
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

  def withSession[T: Encoder](t: T, req: Request[F], res: Response[F]): res.Self =
    withJwt(cookieNames.session, t, req, res)

  def clearSession(req: Request[F], res: Response[F]): res.Self =
    res
      .removeCookie(removalCookie(cookieNames.session, req))
      .removeCookie(removalCookie(cookieNames.user, req))
      .removeCookie(removalCookie(cookieNames.lastId, req))
      .removeCookie(removalCookie(cookieNames.provider, req))
      .addCookie(additionCookie(cookieNames.prompt, Social.SelectAccount, req))

  private def user(headers: Headers): Either[IdentityError, Username] =
    readUser(cookieNames.user, headers)

  def withPicsUser(
    user: UserPayload,
    provider: AuthProvider,
    req: Request[F],
    res: Response[F]
  ) =
    withUser(user, req, res)
      .removeCookie(cookieNames.returnUri)
      .removeCookie(removalCookie(cookieNames.prompt, req))
      .addCookie(additionCookie(cookieNames.lastId, user.username.name, req))
      .addCookie(additionCookie(cookieNames.provider, provider.name, req))

  def withUser[T: Encoder](t: T, req: Request[F], res: Response[F]): res.Self =
    withJwt(cookieNames.user, t, req, res)

  def withJwt[T: Encoder](
    cookieName: String,
    t: T,
    req: Request[F],
    res: Response[F]
  ): res.Self =
    val signed = webJwt.sign(t, 12.hours)
    res.addCookie(additionCookie(cookieName, signed.value, req))

  private def additionCookie(name: String, value: String, req: Request[F]) =
    responseCookie(name, value, Option(HttpDate.MaxValue), req)
  private def removalCookie(name: String, req: Request[F]) =
    responseCookie(name, "", None, req)
  private def responseCookie(
    name: String,
    value: String,
    expires: Option[HttpDate],
    req: Request[F]
  ): ResponseCookie =
    val top = Urls.topDomainFrom(req)
    ResponseCookie(
      name,
      value,
      expires,
      path = cookiePath,
      secure = Urls.isSecure(req),
      httpOnly = true,
      domain = Option.when(top.nonEmpty)(top)
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

  private def onUnauthorized(@unused headers: Headers) =
    SeeOther(Location(Reverse.signIn))
