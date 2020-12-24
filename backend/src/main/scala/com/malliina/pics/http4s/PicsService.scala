package com.malliina.pics.http4s

import java.nio.file.Files

import _root_.play.api.libs.json.{Json, Writes}
import cats.data.NonEmptyList
import cats.effect._
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxFlatten}
import com.malliina.html.UserFeedback
import com.malliina.http.io.HttpClientIO
import com.malliina.pics.PicsStrings.{XClientPic, XKey, XName}
import com.malliina.pics._
import com.malliina.pics.auth.Http4sAuth.TwitterState
import com.malliina.pics.auth.{Http4sAuth, UserPayload}
import com.malliina.pics.html.PicsHtml
import com.malliina.pics.http4s.PicsService.{log, noCache, ranges, version10}
import com.malliina.storage.StorageLong
import com.malliina.values.{AccessToken, Email}
import com.malliina.web.OAuthKeys.{Nonce, State}
import com.malliina.web.TwitterAuthFlow.{OauthTokenKey, OauthVerifierKey}
import com.malliina.web.Utils.randomString
import com.malliina.web._
import controllers.Social
import controllers.Social._
import fs2._
import fs2.concurrent.Topic
import org.http4s.CacheDirective._
import org.http4s.headers.{Accept, Location, `Cache-Control`, `WWW-Authenticate`}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.syntax.literals.http4sLiteralsSyntax
import org.http4s.util.CaseInsensitiveString
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._
import org.http4s.{Callback => _, _}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object PicsService {
  private val log = LoggerFactory.getLogger(getClass)
  val version10 = mediaType"application/vnd.pics.v10+json"
  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)

  def apply(
    conf: PicsConf,
    db: MetaSourceT[IO],
    topic: Topic[IO, PicMessage],
    handler: MultiSizeHandlerIO,
    blocker: Blocker,
    cs: ContextShift[IO],
    timer: Timer[IO]
  ): PicsService = {
    val socials = Socials(conf.social, HttpClientIO())
    apply(
      PicsHtml.build(conf.mode.isProd),
      Http4sAuth(conf.app, cs),
      socials,
      db,
      topic,
      handler,
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
    handler: MultiSizeHandlerIO,
    blocker: Blocker,
    cs: ContextShift[IO],
    timer: Timer[IO]
  ): PicsService = {
    val service = new PicServiceIO(db, handler)(cs)
    new PicsService(service, html, auth, socials, db, topic, handler, blocker)(cs, timer)
  }

  def ranges(headers: Headers) = headers
    .get(Accept)
    .map(_.values.map(_.mediaRange))
    .getOrElse(NonEmptyList.of(MediaRange.`*/*`))
}

class PicsService(
  service: PicServiceIO,
  html: PicsHtml,
  auth: Http4sAuth,
  socials: Socials,
  db: MetaSourceT[IO],
  topic: Topic[IO, PicMessage],
  handler: MultiSizeHandlerIO,
  blocker: Blocker
)(implicit cs: ContextShift[IO], timer: Timer[IO])
  extends BasicService[IO] {
  val pong = "pong"

  def cached(duration: FiniteDuration) = `Cache-Control`(
    NonEmptyList.of(`max-age`(duration), `public`)
  )

  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico")

  val reverseSocial = ReverseSocial
  val cookieNames = auth.cookieNames

  val routes = HttpRoutes.of[IO] {
    case GET -> Root          => SeeOther(Location(uri"/pics"))
    case GET -> Root / "ping" => ok(Json.toJson(AppMeta.default))
    case req @ GET -> Root / "version" =>
      val res = ok(Json.toJson(AppMeta.default))
      renderRanged(req)(res, res)
    case req @ GET -> Root / "pics" =>
      auth
        .authenticate(req.headers)
        .map { e =>
          e.flatMap { picReq =>
            Limits(req.uri.query)
              .map { limits =>
                ListRequest(limits, picReq)
              }
              .left
              .map { errors =>
                badRequest(errors)
              }
          }
        }
        .map { e =>
          e.map { listRequest =>
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
        }
        .flatMap(_.fold(identity, identity))
    case req @ POST -> Root / "pics" =>
      auth
        .authenticate(req.headers)
        .flatMap { e =>
          e.fold(
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
        }
    case req @ POST -> Root / "pics" / KeyParam(key) / "delete" =>
      removeKey(key, Reverse.list, req)
    case req @ DELETE -> Root / KeyParam(key) =>
      removeKey(key, Reverse.drop, req)
    case req @ POST -> Root / "sync" =>
      import cats.implicits._
      val adminUser = PicOwner("malliina123@gmail.com")
      authed(req) { user =>
        if (user.name == adminUser) {
          handler.originals.storage.load(0, 1000000).flatMap { keys =>
            log.info(s"Syncing ${keys.length} keys...")
            keys.toList
              .traverse { key => db.putMetaIfNotExists(key.withUser(adminUser)) }
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
      authedAll(req) { user =>
        val welcomeMessage = fs2.Stream(PicMessage.welcome(user.name, user.readOnly))
        val pings = fs2.Stream.awakeEvery[IO](30.seconds).map(_ => PicMessage.ping)
        val updates = topic.subscribe(1000).drop(1).filter(_.forUser(user.name))
        val toClient = (welcomeMessage ++ pings.merge(updates)).map { message =>
          Text(Json.stringify(Json.toJson(message)))
        }
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
        .find(_.name == cookieNames.provider)
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
          twitter.requestToken(Urls.hostOnly(req) / reverseSocial.twitter.callback.renderString)
          .flatMap { e =>
            // add requesttoken to session
            e.fold(
              err => unauthorized(Errors(err.message)),
              token =>
                SeeOther(Location(Uri.unsafeFromString(twitter.authTokenUrl(token).url))).map {
                  res =>
                    auth.withSession(TwitterState(token), req.isSecured, res)(
                      TwitterState.json
                    )
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
          } yield socials.twitter.validateTwitterCallback(token, requestToken.requestToken, verifier).flatMap { e =>
              e.flatMap(_.email.toRight(OAuthError("Email missing.")))
                .fold(err => unauthorized(Errors(err.message)), ok => userResult(ok, id, req))
            }
          maybe.fold(
            err => {
              log.warn(s"$err in $req")
              unauthorized(Errors.single(s"Invalid callback parameters."))
            },
            identity
          )
        case Google =>
          handleCallbackD(socials.google, reverseSocial.google, req, id)
        case Microsoft =>
          handleCallbackD(socials.microsoft, reverseSocial.microsoft, req, id)
        case GitHub =>
          handleCallbackV(socials.github, reverseSocial.github, req, id)
        case Amazon =>
          handleCallback(
            reverseSocial.amazon,
            req,
            id,
            cb =>
              socials.amazon.validateCallback(cb)
                .map(e => e.map(user => Email(user.username.value)))
          )
        case Facebook =>
          handleCallbackV(socials.facebook, reverseSocial.facebook, req, id)
        case Apple =>
          handleCallbackV(socials.apple, reverseSocial.apple, req, id)
      }
    case GET -> Root / "sign-out" / "leave" =>
      SeeOther(Location(Reverse.signOutCallback)).map { res =>
        import Social._
        auth
          .clearSession(res)
          .removeCookie(ResponseCookie(cookieNames.lastId, "", path = auth.cookiePath))
          .removeCookie(ResponseCookie(cookieNames.provider, "", path = auth.cookiePath))
          .addCookie(cookieNames.prompt, SelectAccount)
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
    handler(size).storage.find(key).flatMap { maybeFile =>
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
            handler.remove(key).flatMap { _ =>
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

  private def start(validator: FlowStart[IO], reverse: SocialRoute, req: Request[IO]) =
    validator.start(Urls.hostOnly(req) / reverse.callback.renderString, Map.empty).flatMap { s =>
      startLoginFlow(s, req.isSecured)
    }

  private def startHinted(
    provider: AuthProvider,
    reverse: SocialRoute,
    validator: LoginHint[IO],
    req: Request[IO]
  ): IO[Response[IO]] = IO {
    val redirectUrl = Urls.hostOnly(req) / reverse.callback.renderString
    val lastIdCookie = req.cookies.find(_.name == cookieNames.lastId)
    val promptValue = req.cookies
      .find(_.name == cookieNames.prompt)
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
    validator.startHinted(redirectUrl, maybeEmail, extra).flatMap { s =>
      startLoginFlow(s, req.isSecured)
    }
  }

  private def startLoginFlow(s: Start, isSecure: Boolean): IO[Response[IO]] = IO {
    val state = randomString()
    val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map { case (k, v) =>
      k -> Utils.urlEncode(v)
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
        .withSession(session, isSecure, res)
        .putHeaders(noCache)
    }
  }

  private def handleCallbackD(
    validator: DiscoveringAuthFlow[Email],
    reverse: SocialRoute,
    req: Request[IO],
    provider: AuthProvider
  ): IO[Response[IO]] =
    handleCallback(
      reverse,
      req,
      provider,
      cb => validator.validateCallback(cb).map(e => e.flatMap(validator.parse))
    )

  private def handleCallbackV(
    validator: CallbackValidator[Email],
    reverse: SocialRoute,
    req: Request[IO],
    provider: AuthProvider
  ): IO[Response[IO]] =
    handleCallback(reverse, req, provider, cb => validator.validateCallback(cb))

  private def handleCallback(
    reverse: SocialRoute,
    req: Request[IO],
    provider: AuthProvider,
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
        email => userResult(email, provider, req)
      )
    }
  }

  private def userResult(
    email: Email,
    provider: AuthProvider,
    req: Request[IO]
  ): IO[Response[IO]] = {
    val returnUri: Uri = req.cookies
      .find(_.name == cookieNames.returnUri)
      .flatMap(c => Uri.fromString(c.content).toOption)
      .getOrElse(Reverse.list)
    SeeOther(Location(returnUri)).map { r =>
      auth.withPicsUser(UserPayload.email(email), req.isSecured, provider, r)
    }
  }

  def stringify(map: Map[String, String]): String =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  def authed(req: Request[IO])(code: PicRequest => IO[Response[IO]]): IO[Response[IO]] =
    auth.authenticate(req.headers).flatMap(_.fold(identity, code))

  def authedAll(req: Request[IO])(code: PicRequest => IO[Response[IO]]): IO[Response[IO]] =
    auth.authenticateAll(req.headers).flatMap(_.fold(identity, code))

  private def render[A: Writes, B](req: Request[IO])(json: A, html: B)(implicit
    w: EntityEncoder[IO, B]
  ): IO[Response[IO]] =
    renderRanged[A, B](req)(ok(Json.toJson(json)), ok(html))

  private def renderRanged[A: Writes, B](
    req: Request[IO]
  )(json: IO[Response[IO]], html: IO[Response[IO]]): IO[Response[IO]] = {
    val rs = ranges(req.headers)
    val qp = req.uri.query.params
    if (qp.get("f").contains("json") || qp.contains("json")) json
    else if (rs.exists(_.satisfies(MediaType.text.html))) html
    else if (rs.exists(_.satisfies(version10))) json
    else if (rs.exists(_.satisfies(MediaType.application.json))) json
    else NotAcceptable(Json.toJson(Errors.single("Not acceptable.")), noCache)
  }

  private def failResize(error: ImageFailure, by: PicRequest): IO[Response[IO]] = error match {
    case UnsupportedFormat(format, supported) =>
      val msg = s"Unsupported format: '$format', must be one of: '${supported.mkString(", ")}'"
      log.error(msg)
      badRequestWith(msg)
    case ImageException(ioe) =>
      val msg = "An I/O error occurred."
      log.error(msg, ioe)
      serverError(msg)
    case ImageReaderFailure(file) =>
      val size = Files.size(file).bytes
      val isReadable = Files.isReadable(file)
      log.error(s"Unable to read image from file '$file'. Size: $size, readable: $isReadable.")
      badRequestWith("Unable to read image.")
    case ResizeException(ipa) =>
      log.error(s"Unable to parse image by '${by.name}'.", ipa)
      badRequestWith("Unable to parse image.")
  }

  private def ok[A](a: A)(implicit w: EntityEncoder[IO, A]) = Ok(a, noCache)
  private def badRequestWith(message: String) = badRequest(Errors.single(message))
  private def badRequest(errors: Errors): IO[Response[IO]] =
    BadRequest(Json.toJson(errors), noCache)
  private def unauthorized(errors: Errors): IO[Response[IO]] = Unauthorized(
    `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
    Json.toJson(errors)
  ).map(r => auth.clearSession(r.removeCookie(cookieNames.provider)))
  private def keyNotFound(key: Key) = notFoundWith(s"Not found: '$key'.")
  private def notFound(req: Request[IO]) = notFoundWith(s"Not found: '${req.uri}'.")
  private def notFoundWith(message: String) = NotFound(Json.toJson(Errors.single(message)), noCache)
  private def serverError(message: String) =
    InternalServerError(Json.toJson(Errors.single(message)), noCache)
}
