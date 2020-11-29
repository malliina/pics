package com.malliina.pics.http4s

import java.nio.file.Files

import _root_.play.api.libs.json.{Json, Writes}
import cats.data.NonEmptyList
import cats.effect._
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxFlatten}
import com.malliina.html.UserFeedback
import com.malliina.http.OkClient
import com.malliina.pics.PicsStrings.{XClientPic, XKey, XName}
import com.malliina.pics.auth.Http4sAuth.TwitterState
import com.malliina.pics.auth.{Http4sAuth, PicsAuth, UserPayload}
import com.malliina.pics.html.PicsHtml
import com.malliina.pics.http4s.PicsService.{log, version10}
import com.malliina.pics.{AppMeta, ContentType, Errors, Key, KeyParam, Limits, ListRequest2, MetaSourceT, MultiSizeHandler, PicMeta, PicMetas, PicRequest2, PicResponse, PicServiceIO, PicSize, Pics, PicsAdded, PicsConf, PicsRemoved, ProfileInfo}
import com.malliina.play.auth.AuthValidator.{Callback, Start}
import com.malliina.play.auth.OAuthKeys.{Nonce, State}
import com.malliina.play.auth.TwitterValidator.{OauthTokenKey, OauthVerifierKey}
import com.malliina.play.auth._
import com.malliina.play.json.JsonMessages
import com.malliina.values.{AccessToken, Email}
import controllers.Social
import controllers.Social._
import fs2._
import fs2.concurrent.Topic
import org.http4s.CacheDirective._
import org.http4s._
import org.http4s.syntax.literals.http4sLiteralsSyntax
import org.http4s.headers.{Accept, Location, `Cache-Control`, `WWW-Authenticate`}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.util.CaseInsensitiveString
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object PicsService {
  private val log = LoggerFactory.getLogger(getClass)
  val version10 = mediaType"application/vnd.pics.v10+json"

  def apply(
    conf: PicsConf,
    db: MetaSourceT[IO],
    topic: Topic[IO, PicMessage],
    blocker: Blocker,
    cs: ContextShift[IO],
    timer: Timer[IO]
  ): PicsService = {
    val socials = Socials(conf.social, OkClient.default)
    apply(
      PicsHtml.build(conf.mode.isProd),
      Http4sAuth(conf.app),
      socials,
      db,
      topic,
      blocker,
      cs,
      timer
    )
  }

  def apply(
    html: PicsHtml,
    auth: Http4sAuth,
    socials: Socials,
    db: MetaSourceT[IO],
    topic: Topic[IO, PicMessage],
    blocker: Blocker,
    cs: ContextShift[IO],
    timer: Timer[IO]
  ): PicsService = {
    val handler = MultiSizeHandler.default()
    val service = new PicServiceIO(db, handler)(cs)
    new PicsService(service, html, auth, socials, db, topic, handler, blocker)(cs, timer)
  }
}

class PicsService(
  service: PicServiceIO,
  html: PicsHtml,
  auth: Http4sAuth,
  socials: Socials,
  db: MetaSourceT[IO],
  topic: Topic[IO, PicMessage],
  handler: MultiSizeHandler,
  blocker: Blocker
)(implicit cs: ContextShift[IO], timer: Timer[IO])
  extends BasicService[IO] {
  val pong = "pong"

  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)

  def cached(duration: FiniteDuration) = `Cache-Control`(
    NonEmptyList.of(`max-age`(duration), `public`)
  )

  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico")

  val reverseSocial = ReverseSocial

  val routes = HttpRoutes.of[IO] {
    case GET -> Root          => SeeOther(Location(uri"/pics"))
    case GET -> Root / "ping" => ok(Json.toJson(AppMeta.default))
    case req @ GET -> Root / "version" =>
      val res = ok(Json.toJson(AppMeta.default))
      renderRanged(req)(res, res)
    case req @ GET -> Root / "pics" =>
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
            render(req)(
              json = Pics(entries),
              html = {
                val feedback = None // UserFeedbacks.flashed(req.rh.flash)
                html.pics(entries, feedback, listRequest.user).tags
              }
            )
          }
        }
        .fold(identity, identity)
    case req @ POST -> Root / "pics" =>
      auth
        .web(req.headers)
        .fold(
          err => err,
          user => {
            val decoder =
              EntityDecoder.binFile[IO](Files.createTempFile("pic", ".jpg").toFile, blocker)
            req.decodeWith(decoder, strict = true) { file =>
              service
                .save(
                  file.toPath,
                  user,
                  req.headers.get(CaseInsensitiveString(XName)).map(_.value)
                )
                .flatMap { keyMeta =>
                  val clientPic = req.headers
                    .get(CaseInsensitiveString(XClientPic))
                    .map(h => Key(h.value))
                    .getOrElse(keyMeta.key)
                  val picMeta = PicMetas.from(keyMeta, req)
                  log.info(s"Saved '${picMeta.key}' by '${user.name}' with URL '${picMeta.url}'.")
                  topic
                    .publish1(
                      PicMessage
                        .AddedMessage(PicsAdded(Seq(picMeta.withClient(clientPic))), user.name)
                    )
                    .flatMap { _ =>
                      Accepted(Json.toJson(PicResponse(picMeta))).map { res =>
                        res.putHeaders(
                          Location(Uri.unsafeFromString(picMeta.url.url)),
                          Header(XKey, picMeta.key.key),
                          Header(XClientPic, clientPic.key)
                        )
                      }
                    }
                }
            }
          }
        )
    case req @ POST -> Root / "pics" / KeyParam(key) / "delete" =>
      removeKey(key, Reverse.list, req)
    case req @ DELETE -> Root / KeyParam(key) =>
      removeKey(key, Reverse.drop, req)
    case req @ POST -> Root / "sync" =>
      import cats.implicits._
      authed(req) { user =>
        if (user.name == PicsAuth.AdminUser) {
          IO.fromFuture(IO(handler.originals.storage.load(0, 1000000))).flatMap { keys =>
            log.info(s"Syncing ${keys.length} keys...")
            keys.toList
              .traverse { key => db.putMetaIfNotExists(key.withUser(PicsAuth.AdminUser)) }
              .flatMap { changes =>
                log.info(s"Sync complete. Upserted ${changes.sum} rows.")
                SeeOther(Location(Reverse.drop))
              }
          }
        } else {
          unauthorized(Errors.single("Admin required."))
        }
      }
    case req @ GET -> Root / "sockets" =>
      authed(req) { user =>
        val welcomeMessage = fs2.Stream(Json.toJson(ProfileInfo(user.name, user.readOnly)))
        val pings =
          fs2.Stream.awakeEvery[IO](30.seconds).map(d => JsonMessages.ping)
        val updates =
          topic.subscribe(1000).filter(_.forUser(user.name)).drop(1).map(msg => Json.toJson(msg))
        val toClient =
          (welcomeMessage ++ pings.merge(updates)).map(json => Text(Json.stringify(json)))
        val fromClient: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
          case Text(message, _) => IO(log.info(message))
          case f                => IO(log.debug(s"Unknown WebSocket frame: $f"))
        }
        WebSocketBuilder[IO].build(toClient, fromClient)
      }
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
            // add requesttoken to session
            e.fold(
              err => unauthorized(Errors(err.message)),
              token =>
                SeeOther(Location(Uri.unsafeFromString(twitter.authTokenUrl(token).url))).map {
                  res =>
                    auth.withSession(TwitterState(token), res)(TwitterState.json)
                }
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
          val maybe = for {
            token <- params
              .get(OauthTokenKey)
              .map(AccessToken.apply)
              .toRight(OAuthError(s"Missing $OauthTokenKey query parameter."))
            requestToken <- auth
              .session[TwitterState](req.headers)
              .left
              .map(e => OAuthError(s"Session failure."))
            verifier <- params
              .get(OauthVerifierKey)
              .toRight(OAuthError(s"Missing $OauthVerifierKey query paramater."))
          } yield IO
            .fromFuture(
              IO(
                socials.twitter.validateTwitterCallback(token, requestToken.requestToken, verifier)
              )
            )
            .flatMap { e =>
              e.flatMap(_.email.toRight(OAuthError("Email missing.")))
                .fold(err => unauthorized(Errors(err.message)), ok => userResult(ok, req))
            }
          maybe.fold(
            err => {
              log.warn(s"$err in $req")
              unauthorized(Errors.single(s"Invalid callback parameters."))
            },
            identity
          )
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
    case GET -> Root / "sign-out" / "leave" =>
      SeeOther(Location(Reverse.signOutCallback)).map { res =>
        import Social._
        auth
          .clearSession(res)
          .removeCookie(ResponseCookie(LastIdCookie, "", path = auth.cookiePath))
          .removeCookie(ResponseCookie(ProviderCookie, "", path = auth.cookiePath))
          .addCookie(PromptCookie, SelectAccount)
      }
    case GET -> Root / "sign-out" =>
      // TODO .flashing("message" -> "You have now logged out.")
      SeeOther(Location(Reverse.list))
    case GET -> Root / "legal" / "privacy" =>
      ok(html.privacyPolicy.tags)
    case GET -> Root / "support" =>
      ok(html.support.tags)
    case req @ GET -> Root / ".well-known" / "apple-developer-domain-association.txt" =>
      StaticFile
        .fromResource(
          "apple-developer-domain-association.txt",
          blocker,
          Option(req),
          preferGzipped = true
        )
        .fold(notFound(req))(_.putHeaders(cached(1.hour)).pure[IO])
        .flatten
    case req @ GET -> Root / KeyParam(key) / "small" =>
      sendPic(key, PicSize.Small, req)
    case req @ GET -> Root / KeyParam(key) / "medium" =>
      sendPic(key, PicSize.Medium, req)
    case req @ GET -> Root / KeyParam(key) / "large" =>
      sendPic(key, PicSize.Large, req)
    case req @ GET -> Root / rest if ContentType.parse(rest).exists(_.isImage) =>
      PicSize(req)
        .map { size =>
          sendPic(Key(rest), size, req)
        }
        .recover { err =>
          badRequest(Errors(NonEmptyList.of(err)))
        }
  }

  private def sendPic(key: Key, size: PicSize, req: Request[IO]) =
    IO.fromFuture(IO(handler(size).storage.find(key))).flatMap { maybeFile =>
      maybeFile
        .map { file =>
          StaticFile
            .fromFile(file.file.toFile, blocker, Option(req))
            .map(_.putHeaders(cached(365.days)))
            .getOrElseF(notFound(req))
        }
        .getOrElse {
          notFoundWith(s"Not found: '$key'.")
        }
    }

  private def removeKey(key: Key, redir: Uri, req: Request[IO]) =
    authed(req) { user =>
      if (user.readOnly) {
        IO(log.warn(s"User '${user.name}' is not authorized to delete '$key'.")).flatMap { _ =>
          unauthorized(Errors.single(s"Unauthorized."))
        }
      } else {
        db.remove(key, user.name).flatMap { wasDeleted =>
          if (wasDeleted) {
            log.info(s"Key '$key' removed by '${user.name}' from '$req'.")
            IO.fromFuture(IO(handler.remove(key))).flatMap { _ =>
              topic.publish1(PicMessage.RemovedMessage(PicsRemoved(Seq(key)), user.name)).flatMap {
                _ =>
                  renderRanged(req)(
                    json = Accepted(Json.toJson(Json.obj("message" -> "ok"))),
                    html = SeeOther(Location(redir))
                  )
              }
            }
          } else {
            log.error(s"Key not found: '$key'.")
            keyNotFound(key)
          }
        }
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
  ): IO[Response[IO]] = IO {
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
    (redirectUrl, maybeEmail, extra)
  }.flatMap { case (redirectUrl, maybeEmail, extra) =>
    IO.fromFuture(IO(validator.startHinted(redirectUrl, maybeEmail, extra))).flatMap { s =>
      startLoginFlow(s)
    }
  }

  private def startLoginFlow(s: Start): IO[Response[IO]] = IO {
    val state = CodeValidator.randomString()
    val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map { case (k, v) =>
      k -> AuthValidator.urlEncode(v)
    }
    val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
    log.info(s"Redirecting to '$url' with state '$state'...")
    val sessionParams = Seq(State -> state) ++ s.nonce
      .map(n => Seq(Nonce -> n))
      .getOrElse(Nil)
    (url, sessionParams)
  }.flatMap { case (url, sessionParams) =>
    SeeOther(Location(Uri.unsafeFromString(url.url))).map { res =>
      val session = Json.toJsObject(sessionParams.toMap)
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

  def stringify(map: Map[String, String]): String =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  def authed(req: Request[IO])(code: PicRequest2 => IO[Response[IO]]) =
    auth.web(req.headers).map(code).fold(identity, identity)

  private def render[A: Writes, B](req: Request[IO])(json: A, html: B)(implicit
    w: EntityEncoder[IO, B]
  ): IO[Response[IO]] =
    renderRanged[A, B](req)(ok(Json.toJson(json)), ok(html))

  private def renderRanged[A: Writes, B](
    req: Request[IO]
  )(json: IO[Response[IO]], html: IO[Response[IO]]): IO[Response[IO]] = {
    val rs = ranges(req)
    val qp = req.uri.query.params
    if (qp.get("f").contains("json") || qp.contains("json")) json
    else if (rs.exists(_.satisfies(MediaType.text.html))) html
    else if (rs.exists(_.satisfies(version10))) json
    else if (rs.exists(_.satisfies(MediaType.application.json))) json
    else NotAcceptable(Json.toJson(Errors.single("Not acceptable.")), noCache)
  }

  private def ranges(req: Request[IO]) = req.headers
    .get(Accept)
    .map(_.values.map(_.mediaRange))
    .getOrElse(NonEmptyList.of(MediaRange.`*/*`))

  private def ok[A](a: A)(implicit w: EntityEncoder[IO, A]) = Ok(a, noCache)

  private def badRequest(errors: Errors): IO[Response[IO]] =
    BadRequest(Json.toJson(errors), noCache)
  private def unauthorized(errors: Errors): IO[Response[IO]] = Unauthorized(
    `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
    Json.toJson(errors)
  ).map(r => auth.clearSession(r.removeCookie(ProviderCookie)))
  private def keyNotFound(key: Key) = notFoundWith(s"Not found: '$key'.")
  private def notFound(req: Request[IO]) = notFoundWith(s"Not found: '${req.uri}'.")
  private def notFoundWith(message: String) = NotFound(Json.toJson(Errors.single(message)), noCache)
}