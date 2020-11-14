package com.malliina.pics.http4s

import _root_.play.api.libs.json.{Json, Writes}
import cats.data.NonEmptyList
import cats.effect._
import com.malliina.html.UserFeedback
import com.malliina.http.OkClient
import com.malliina.pics.auth.{Http4sAuth, UserPayload}
import com.malliina.pics.html.PicsHtml
import com.malliina.pics.http4s.PicsService.log
import com.malliina.pics.{
  AppMeta,
  ContentType,
  Errors,
  Key,
  Limits,
  ListRequest2,
  MetaSourceT,
  MultiSizeHandler,
  PicMeta,
  PicMetas,
  PicRequest2,
  PicSize,
  Pics,
  PicsConf
}
import com.malliina.play.auth.AuthValidator.{Callback, Start}
import com.malliina.play.auth.OAuthKeys.{Nonce, State}
import com.malliina.play.auth.TwitterValidator.{OauthTokenKey, OauthVerifierKey}
import com.malliina.play.auth._
import com.malliina.values.{AccessToken, Email}
import controllers.Social
import controllers.Social._
import org.http4s.CacheDirective._
import org.http4s._
import org.http4s.headers.{Accept, Location, `Cache-Control`, `WWW-Authenticate`}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt

object PicsService {
  private val log = LoggerFactory.getLogger(getClass)

  def apply(
    conf: PicsConf,
    db: MetaSourceT[IO],
    blocker: Blocker,
    cs: ContextShift[IO]
  ): PicsService = {
    val socials = Socials(conf.social, OkClient.default)
    apply(PicsHtml.build(conf.mode.isProd), Http4sAuth(conf.app), socials, db, blocker, cs)
  }

  def apply(
    html: PicsHtml,
    auth: Http4sAuth,
    socials: Socials,
    db: MetaSourceT[IO],
    blocker: Blocker,
    cs: ContextShift[IO]
  ): PicsService =
    new PicsService(html, auth, socials, db, MultiSizeHandler.default(), blocker)(cs)
}

class PicsService(
  html: PicsHtml,
  auth: Http4sAuth,
  socials: Socials,
  db: MetaSourceT[IO],
  handler: MultiSizeHandler,
  blocker: Blocker
)(implicit cs: ContextShift[IO])
  extends BasicService[IO] {
  val pong = "pong"

  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)

  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico")

  val reverseSocial = ReverseSocial

  val routes = HttpRoutes.of[IO] {
    case GET -> Root             => TemporaryRedirect(Location(uri"/pics"))
    case GET -> Root / "ping"    => ok(Json.toJson(AppMeta.default))
    case GET -> Root / "version" => ok(Json.toJson(AppMeta.default))
    case req @ GET -> Root / "pics" =>
      val ranges = req.headers
        .get(Accept)
        .map(_.values.map(_.mediaRange))
        .getOrElse(NonEmptyList.of(MediaRange.`*/*`))
      auth
        .web(req.headers)
        .flatMap { picReq =>
          Limits(req.uri.query)
            .map { limits =>
              ListRequest2(limits, picReq)
            }
            .left
            .map { errors =>
              badRequest(errors)
            }
        }
        .map { listRequest =>
          db.load(listRequest.offset, listRequest.limit, listRequest.user.name).flatMap { keys =>
            val entries = keys.map { key =>
              PicMetas.from(key, req)
            }
            render(ranges)(
              json = Pics(entries),
              html = {
                val feedback = None // UserFeedbacks.flashed(req.rh.flash)
                html.pics(entries, feedback, listRequest.user).tags
              }
            )
          }
        }
        .fold(identity, identity)
    case req @ GET -> Root / "drop" =>
      authed(req) { user =>
        val created: Option[PicMeta] = None
        val feedback: Option[UserFeedback] = None
        ok(html.drop(created, feedback, user).tags)
      }
    case GET -> Root / "sign-up" =>
      ok(html.signUp().tags)
    case req @ GET -> Root / "sign-in" =>
      val reverseSocial = ReverseSocial
      req.cookies
        .find(_.name == Social.ProviderCookie)
        .flatMap { cookie =>
          AuthProvider.forString(cookie.content).toOption
        }
        .map {
          case Google    => reverseSocial.google
          case Microsoft => reverseSocial.microsoft
          case Facebook  => reverseSocial.facebook
          case GitHub    => reverseSocial.github
          case Amazon    => reverseSocial.amazon
          case Twitter   => reverseSocial.twitter
          case Apple     => reverseSocial.apple
        }
        .map(r => TemporaryRedirect(r.start))
        .getOrElse(ok(html.signIn(None).tags))
    case req @ GET -> Root / "sign-in" / AuthProvider(id) =>
      id match {
        case Twitter =>
          val twitter = socials.twitter
          IO.fromFuture(
            IO(
              twitter
                .requestToken(Urls.hostOnly(req) / reverseSocial.twitter.callback.renderString)
            )
          ).flatMap { e =>
            e.fold(
              err => unauthorized(Errors(err.message)),
              token => SeeOther(Location(Uri.unsafeFromString(twitter.authTokenUrl(token).url)))
            )
          }
        case Google =>
          startHinted(id, reverseSocial.google, socials.google, req)
        case Microsoft =>
          startHinted(id, reverseSocial.microsoft, socials.microsoft, req)
        case GitHub =>
          start(socials.github, reverseSocial.github, req)
        case Amazon =>
          start(socials.amazon, reverseSocial.amazon, req)
        case Facebook =>
          start(socials.facebook, reverseSocial.facebook, req)
        case Apple =>
          start(socials.apple, reverseSocial.apple, req)
      }
    case req @ GET -> Root / "sign-in" / "callbacks" / AuthProvider(id) =>
      id match {
        case Twitter =>
          val params = req.uri.query.params
          val session = auth.session[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
          val maybe = for {
            token <- params.get(OauthTokenKey).map(AccessToken.apply)
            requestToken <- session.get(RequestToken.Key).map(AccessToken.apply)
            verifier <- params.get(OauthVerifierKey)
          } yield IO
            .fromFuture(IO(socials.twitter.validateTwitterCallback(token, requestToken, verifier)))
            .flatMap { e =>
              e.flatMap(_.email.toRight(OAuthError("Email missing.")))
                .fold(err => unauthorized(Errors(err.message)), ok => userResult(ok, req))
            }
          maybe.getOrElse(unauthorized(Errors.single(s"Invalid callback parameters.")))
        case Google =>
          handleCallback(socials.google, reverseSocial.google, req)
        case Microsoft =>
          handleCallback(socials.microsoft, reverseSocial.microsoft, req)
        case GitHub =>
          handleCallback(socials.github, reverseSocial.github, req)
        case Amazon =>
          handleCallback(
            reverseSocial.amazon,
            req,
            cb =>
              IO.fromFuture(IO(socials.amazon.validateCallback(cb)))
                .map(e => e.map(user => Email(user.username.value)))
          )
        case Facebook =>
          handleCallback(socials.facebook, reverseSocial.facebook, req)
        case Apple =>
          handleCallback(socials.apple, reverseSocial.apple, req)
      }
    case req @ GET -> Root / rest if ContentType.parse(rest).exists(_.isImage) =>
      PicSize(req)
        .map { size =>
          val key = Key(rest)
          IO.fromFuture(IO(handler(size).storage.find(key))).flatMap { maybeFile =>
            maybeFile
              .map { file =>
                StaticFile
                  .fromFile(file.file.toFile, blocker, Option(req))
                  .map(
                    _.putHeaders(`Cache-Control`(NonEmptyList.of(`max-age`(365.days), `public`)))
                  )
                  .getOrElseF {
                    notFound(req)
                  }
              }
              .getOrElse {
                notFoundWith(s"Not found: '$key'.")
              }
          }
        }
        .recover { err =>
          badRequest(Errors(NonEmptyList.of(err)))
        }
  }

  private def start(validator: AuthValidator, reverse: SocialRoute, req: Request[IO]) =
    IO.fromFuture(
      IO(validator.start(Urls.hostOnly(req) / reverse.callback.renderString, Map.empty))
    ).flatMap { s =>
      startLoginFlow(s)
    }

  private def startHinted(
    provider: AuthProvider,
    reverse: SocialRoute,
    validator: LoginHintSupport,
    req: Request[IO]
  ): IO[Response[IO]] = {
    val redirectUrl = Urls.hostOnly(req) / reverse.callback.renderString
    val lastIdCookie = req.cookies.find(_.name == LastIdCookie)
    val promptValue = req.cookies
      .find(_.name == PromptCookie)
      .map(_.content)
      .orElse(Option(SelectAccount).filter(_ => lastIdCookie.isEmpty))
    val extra = promptValue.map(c => Map(PromptKey -> c)).getOrElse(Map.empty)
    val maybeEmail = lastIdCookie.map(_.content).filter(_ => extra.isEmpty)
    maybeEmail.foreach { hint =>
      log.info(s"Starting OAuth flow with $provider using login hint '$hint'...")
    }
    promptValue.foreach { prompt =>
      log.info(s"Starting OAuth flow with $provider using prompt '$prompt'...")
    }

    IO.fromFuture(IO(validator.startHinted(redirectUrl, maybeEmail, extra))).flatMap { s =>
      startLoginFlow(s)
    }
  }

  private def startLoginFlow(s: Start): IO[Response[IO]] = {
    val state = CodeValidator.randomString()
    val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map { case (k, v) =>
      k -> AuthValidator.urlEncode(v)
    }
    val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
    log.info(s"Redirecting to '$url' with state '$state'...")
    val sessionParams = Seq(State -> state) ++ s.nonce
      .map(n => Seq(Nonce -> n))
      .getOrElse(Nil)
    SeeOther(Location(Uri.unsafeFromString(url.url))).map { res =>
      val session = Json.toJson(sessionParams.toMap)
      auth
        .withSession(session, res)
        .putHeaders(noCache)
    }
  }

  private def handleCallback(
    validator: DiscoveringCodeValidator[Email],
    reverse: SocialRoute,
    req: Request[IO]
  ): IO[Response[IO]] = handleCallback(
    reverse,
    req,
    cb => IO.fromFuture(IO(validator.validateCallback(cb))).map(e => e.flatMap(validator.parse))
  )

  private def handleCallback(
    validator: CodeValidator[Email, Email],
    reverse: SocialRoute,
    req: Request[IO]
  ): IO[Response[IO]] =
    handleCallback(reverse, req, cb => IO.fromFuture(IO(validator.validateCallback(cb))))

  private def handleCallback(
    reverse: SocialRoute,
    req: Request[IO],
    validate: Callback => IO[Either[AuthError, Email]]
  ): IO[Response[IO]] = {
    val params = req.uri.query.params
    val session = auth.session[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
    val cb = Callback(
      params.get(OAuthKeys.State),
      session.get(State),
      params.get(OAuthKeys.CodeKey),
      session.get(Nonce),
      Urls.hostOnly(req) / reverse.callback.renderString
    )
    validate(cb).flatMap { e =>
      e.fold(
        err => unauthorized(Errors(err.message)),
        email => userResult(email, req)
      )
    }
  }

  private def userResult(email: Email, req: Request[IO]): IO[Response[IO]] = {
    val returnUri: Uri = req.cookies
      .find(_.name == auth.returnUriKey)
      .flatMap(c => Uri.fromString(c.content).toOption)
      .getOrElse(Reverse.list)
    SeeOther(Location(returnUri)).map { r =>
      auth
        .withUser(UserPayload.email(email), r)
        .removeCookie(auth.returnUriKey)
        .addCookie(auth.lastIdKey, email.email, Option(HttpDate.MaxValue))
    }
  }

  private def handleEmailCallback(
    reverse: SocialRoute,
    validator: CodeValidator[Email, Email],
    req: Request[IO]
  ) = {
    val params = req.uri.query.params
    val session = auth.session[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
    val cb = Callback(
      params.get(OAuthKeys.State),
      session.get(State),
      params.get(OAuthKeys.CodeKey),
      session.get(Nonce),
      Urls.hostOnly(req) / reverse.callback.renderString
    )
    IO.fromFuture(IO(validator.validateCallback(cb))).flatMap { e =>
      e.fold(
        err => unauthorized(Errors(err.message)),
        email => {
          val returnUri: Uri = req.cookies
            .find(_.name == auth.returnUriKey)
            .flatMap(c => Uri.fromString(c.content).toOption)
            .getOrElse(Reverse.list)
          SeeOther(Location(returnUri)).map { r =>
            auth
              .withUser(UserPayload.email(email), r)
              .removeCookie(auth.returnUriKey)
              .addCookie(auth.lastIdKey, email.email, Option(HttpDate.MaxValue))
          }
        }
      )
    }
  }

  def stringify(map: Map[String, String]): String =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  def authed(req: Request[IO])(code: PicRequest2 => IO[Response[IO]]) =
    auth.web(req.headers).map(code).fold(identity, identity)

  def render[A: Writes, B](
    ranges: NonEmptyList[MediaRange]
  )(json: A, html: B)(implicit w: EntityEncoder[IO, B]): IO[Response[IO]] =
    if (ranges.exists(_.satisfies(MediaType.text.html))) ok(html)
    else ok(Json.toJson(json))

  def ok[A](a: A)(implicit w: EntityEncoder[IO, A]) = Ok(a, noCache)

  def badRequest(errors: Errors): IO[Response[IO]] = BadRequest(Json.toJson(errors))
  def unauthorized(errors: Errors): IO[Response[IO]] = Unauthorized(
    `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
    Json.toJson(errors)
  ).map(r => auth.clearSession(r.removeCookie(ProviderCookie)))
  def notFound(req: Request[IO]) = notFoundWith(s"Not found: '${req.uri}'.")
  def notFoundWith(message: String) = NotFound(Json.toJson(Errors.single(message)))
}
