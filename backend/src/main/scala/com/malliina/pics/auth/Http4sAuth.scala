package com.malliina.pics.auth

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.all.*
import com.malliina.http.{Errors, HttpClient, SingleError}
import com.malliina.pics.auth.CredentialsResult.{AccessTokenResult, IdTokenResult, NoCredentials}
import com.malliina.pics.auth.Http4sAuth.log
import com.malliina.pics.db.{PicsDatabase, UserRow}
import com.malliina.pics.http4s.PicsService.version10
import com.malliina.pics.http4s.{PicsBasicService, PicsService, Reverse, Urls}
import com.malliina.pics.{AppConf, Language, PicRequest, Role}
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, ErrorMessage, IdToken}
import com.malliina.web.{AuthError, CognitoAccessValidator, CognitoIdValidator, CognitoUser, OAuthError}
import io.circe.*
import io.circe.syntax.EncoderOps
import org.http4s.*
import org.http4s.Credentials.Token
import org.http4s.headers.{Authorization, Cookie, Location, `WWW-Authenticate`}
import org.typelevel.ci.CIStringSyntax

import scala.annotation.unused
import scala.concurrent.duration.DurationInt

object Http4sAuth:
  private val log = AppLogger(getClass)

  def default[F[_]: Sync](conf: AppConf, db: PicsDatabase[F], http: HttpClient[F]): Http4sAuth[F] =
    Http4sAuth(
      JWT(conf.secret),
      Validators.picsAccess,
      Validators.picsId,
      Validators.google(http),
      db,
      CookieConf.pics
    )

  case class TwitterState(requestToken: AccessToken) derives Codec.AsObject

class Http4sAuth[F[_]: Sync](
  val webJwt: JWT,
  ios: CognitoAccessValidator,
  android: CognitoIdValidator,
  google: GoogleTokenAuth[F],
  db: PicsDatabase[F],
  val cookieNames: CookieConf
) extends PicsBasicService[F]:
  val cookiePath = Option("/")
  val F = Sync[F]

  def authenticate(headers: Headers): F[Either[F[Response[F]], PicRequest]] =
    val rs = PicsService.ranges(headers)
    if rs.exists(r => r.satisfiedBy(MediaType.text.html)) then web(headers)
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
    val userAuth: F[Either[IdentityError, PicUser]] =
      user(headers).fold(
        _ => readJwt(headers),
        user => userOrDefault(user).map(u => Right(u))
      )
    userAuth.map: e =>
      e.fold(
        {
          case MissingCredentials(_, headers)
              if !headers.get[Cookie].exists(_.values.exists(_.name == cookieNames.provider)) =>
            Right(PicRequest.anon(headers))
          case _ => Left(onUnauthorized(headers))
        },
        user => Right(PicRequest.user(user, headers))
      )

  private def web(headers: Headers): F[Either[F[Response[F]], PicRequest]] =
    user(headers).fold(
      {
        case MissingCredentials(_, headers)
            if !headers.get[Cookie].exists(_.values.exists(_.name == cookieNames.provider)) =>
          F.pure(Right(PicRequest.anon(headers)))
        case _ => F.pure(Left(onUnauthorized(headers)))
      },
      user =>
        userOrDefault(user).map: u =>
          Right(PicRequest.user(u, headers))
    )

  private def jwt(headers: Headers): F[Either[F[Response[F]], PicRequest]] =
    readJwt(headers).map: e =>
      e.fold(
        {
          case MissingCredentials(message, headers) =>
            Right(PicRequest.anon(headers))
          case TokenError(error, headers) =>
            log.warn(s"Unauthorized request errored with $error, headers $headers")
            Left(unauthorized(Errors(SingleError(error.message, error.key))))
        },
        user => Right(PicRequest.user(user, headers))
      )

  private def readJwt(headers: Headers): F[Either[IdentityError, PicUser]] =
    token(headers) match
      case AccessTokenResult(token) =>
        db
          .userByToken(token)
          .map: opt =>
            opt
              .flatMap: u =>
                u.cognito
                  .map(id => Cognito(id))
                  .orElse(u.email.map(e => SocialEmail(e)))
                  .map: sub =>
                    PicUser(UserPayload(u.username, sub), u.role, u.language)
              .toRight[IdentityError]:
                TokenError(OAuthError(ErrorMessage("Invalid access token.")), headers)
      case IdTokenResult(token) =>
        cognitoUser(token)
          .fold(
            _ => googleUser(token),
            cognito => F.pure(Right(cognito))
          )
          .flatMap: e =>
            e.fold(
              error => F.pure(Left(TokenError(error, headers))),
              user => userOrDefault(user).map(u => Right(u))
            )
      case NoCredentials(headers) =>
        F.pure(Left(MissingCredentials("Credentials required.", headers)))

  private def cognitoUser(token: IdToken): Either[AuthError, UserPayload] =
    cognitoAuth(token).map(u => UserPayload.cognito(u))

  private def cognitoAuth(token: IdToken): Either[AuthError, CognitoUser] =
    AccessToken
      .build(token.value)
      .left
      .map(err => OAuthError(err))
      .flatMap(at => ios.validate(at))
      .orElse(android.validate(token))

  private def googleUser(token: IdToken): F[Either[AuthError, UserPayload]] =
    google
      .validate(token)
      .map(e => e.map(email => UserPayload.email(email)))

  private def userOrDefault(user: UserPayload): F[PicUser] =
    db.loadUser(user)
      .map: userOpt =>
        orDefault(user, userOpt)

  private def orDefault(user: UserPayload, userOpt: Option[UserRow]): PicUser =
    val lang = userOpt.map(_.language).getOrElse(Language.default)
    val role = userOpt.map(_.role).getOrElse(Role.Normal)
    PicUser(user, role, lang)

  def token(headers: Headers): CredentialsResult = headers
    .get[Authorization]
    .fold[CredentialsResult](NoCredentials(headers)): h =>
      h.credentials match
        case Token(scheme, token) =>
          if scheme == ci"token" then
            AccessToken
              .build(token)
              .map(at => AccessTokenResult(at))
              .getOrElse(NoCredentials(headers))
          else IdToken.build(token).map(it => IdTokenResult(it)).getOrElse(NoCredentials(headers))
        case _ =>
          NoCredentials(headers)

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

  private def user(headers: Headers): Either[IdentityError, UserPayload] =
    readUser(cookieNames.user, headers)

  def withPicsUser(
    user: UserPayload,
    provider: AuthProvider,
    req: Request[F],
    res: Response[F]
  ): Response[F] =
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

  private def readUser(cookieName: String, headers: Headers): Either[IdentityError, UserPayload] =
    read[UserPayload](cookieName, headers)

  private def read[T: Decoder](cookieName: String, headers: Headers): Either[IdentityError, T] =
    for
      header <- headers.get[Cookie].toRight(MissingCredentials("Cookie parsing error.", headers))
      cookie <- header.values
        .find(_.name == cookieName)
        .toRight(MissingCredentials(s"Cookie not found: '$cookieName'.", headers))
        .flatMap(c =>
          IdToken.build(c.content).left.map(err => MissingCredentials(err.message, headers))
        )
      t <- webJwt
        .verify[T](cookie)
        .left
        .map: err =>
          TokenError(err, headers)
    yield t

  private def onUnauthorized(@unused headers: Headers) =
    SeeOther(Location(Reverse.signIn))

  private def unauthorized(errors: Errors): F[Response[F]] = Unauthorized(
    `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
    errors.asJson
  )
