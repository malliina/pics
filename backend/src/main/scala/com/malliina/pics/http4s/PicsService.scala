package com.malliina.pics.http4s

import cats.Show
import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all.{catsSyntaxApplicativeError, catsSyntaxApplicativeId, toFlatMapOps, toFunctorOps, toTraverseOps}
import com.malliina.html.UserFeedback
import com.malliina.http.{Errors, HttpClient}
import com.malliina.http4s.FormDecoders
import com.malliina.pics.*
import com.malliina.pics.PicsStrings.{XClientPic, XKey, XName}
import com.malliina.pics.auth.AuthProvider.*
import com.malliina.pics.auth.Http4sAuth.TwitterState
import com.malliina.pics.auth.Social.*
import com.malliina.pics.auth.{AppleResponse, AuthProvider, Http4sAuth, UserPayload}
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.html.PicsHtml
import com.malliina.pics.http4s.PicsService.{log, ranges, version10}
import com.malliina.storage.StorageLong
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, Email}
import com.malliina.web.*
import com.malliina.web.OAuthKeys.{Nonce, State}
import com.malliina.web.TwitterAuthFlow.{OauthTokenKey, OauthVerifierKey}
import com.malliina.web.Utils.randomString
import fs2.concurrent.Topic
import fs2.io.file.Path as FS2Path
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Json}
import org.http4s.CacheDirective.*
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.headers.{Accept, Location, `Cache-Control`, `WWW-Authenticate`}
import org.http4s.server.middleware.CSRF
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.syntax.literals.mediaType
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import org.http4s.{Callback as _, *}
import org.typelevel.ci.CIStringSyntax

import java.nio.file.Files
import scala.annotation.unused
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object PicsService:
  private val log = AppLogger(getClass)
  val version10 = mediaType"application/vnd.pics.v10+json"
  val version10String = Show[MediaType].show(version10)

  def default[F[_]: Async](
    conf: => PicsConf,
    db: PicsDatabase[F],
    topic: Topic[F, PicMessage],
    handler: MultiSizeHandler[F],
    http: HttpClient[F],
    csrf: CSRF[F, F],
    csrfConf: CSRFConf
  ): PicsService[F] =
    PicsService(
      PicService(db, handler),
      PicsHtml.build(conf.isProdBuild, csrfConf),
      Http4sAuth.default(conf.app, db, http),
      Socials(conf.social, http),
      db,
      topic,
      handler,
      csrf
    )

  def ranges(headers: Headers) = headers
    .get[Accept]
    .map(_.values.map(_.mediaRange))
    .getOrElse(NonEmptyList.of(MediaRange.`*/*`))

class PicsService[F[_]: Async](
  service: PicService[F],
  html: PicsHtml,
  auth: Http4sAuth[F],
  socials: Socials[F],
  db: MetaSourceT[F],
  topic: Topic[F, PicMessage],
  handler: MultiSizeHandler[F],
  csrf: CSRF[F, F]
) extends PicsBasicService[F]
  with FormDecoders[F]
  with FeedbackJson:
  val pong = "pong"
  val F = Async[F]

  def cached(duration: FiniteDuration) = `Cache-Control`(
    NonEmptyList.of(`max-age`(duration), `public`)
  )

  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico")

  val reverseSocial = ReverseSocial
  val cookieNames = auth.cookieNames

  def routes(sockets: WebSocketBuilder2[F]) = HttpRoutes.of[F] {
    case GET -> Root            => SeeOther(Location(uri"/pics"))
    case GET -> Root / "ping"   => ok(AppMeta.default.asJson)
    case GET -> Root / "health" => ok(AppMeta.default.asJson)
    case req @ GET -> Root / "version" =>
      val res = ok(AppMeta.default.asJson)
      renderRanged(req)(res, res)
    case req @ GET -> Root / "pics" =>
      auth
        .authenticate(req.headers)
        .map: e =>
          e.flatMap: picReq =>
            Limits(req.uri.query)
              .map: limits =>
                ListRequest(limits, picReq)
              .left
              .map: errors =>
                badRequest(errors)
        .map: e =>
          e.map: listRequest =>
            db.load(listRequest.offset, listRequest.limit, listRequest.user.name)
              .flatMap: keys =>
                val entries = keys.map: key =>
                  PicMetas.from(key, req)
                renderRanged(req)(
                  json = ok(Pics(entries)),
                  html = csrfOk: token =>
                    val feedback = req.feedbackAs[UserFeedback]
                    html.pics(entries, feedback, listRequest.user, listRequest.limits, token)
                ).clearFeedback
        .flatMap(_.fold(identity, identity))
    case req @ POST -> Root / "pics" =>
      auth
        .authenticate(req.headers)
        .flatMap: e =>
          e.fold(
            err => delay(log.error(s"Failed to authenticate $req.")).flatMap(_ => err),
            user =>
              val tempFile = Files.createTempFile("pic", ".jpg")
              val logging =
                delay(
                  log.info(s"Saving new pic by '${user.name}' to '${tempFile.toAbsolutePath}'...")
                )
              val decoder = EntityDecoder.binFile[F](FS2Path.fromNioPath(tempFile))
              val receive = req.decodeWith(decoder, strict = true): file =>
                service
                  .save(
                    file,
                    user,
                    req.headers.get(XName).map(_.head.value)
                  )
                  .flatMap: keyMeta =>
                    val clientKey = req.headers
                      .get(XClientPic)
                      .map(h => Key(h.head.value))
                      .getOrElse(keyMeta.key)
                    val picMeta = PicMetas.from(keyMeta, req)
                    log.info(s"Saved '${picMeta.key}' by '${user.name}' with URL '${picMeta.url}'.")
                    topic
                      .publish1(
                        PicMessage
                          .AddedMessage(PicsAdded(Seq(picMeta.withClientKey(clientKey))), user.name)
                      )
                      .flatMap: _ =>
                        log.info(s"Published added message for '${picMeta.key}' by ${user.name}.")
                        Accepted(PicResponse(picMeta).asJson).map: res =>
                          res.putHeaders(
                            Location(Uri.unsafeFromString(picMeta.url.url)),
                            Header.Raw(XKey, picMeta.key.key),
                            Header.Raw(XClientPic, clientKey.key)
                          )
              for
                _ <- logging
                r <- receive
              yield r
          )
    case req @ POST -> Root / "pics" / "delete" =>
      jsonOrForm[Deletion](req): (del, _) =>
        removeKey(del.key, Reverse.drop, req)
    case req @ POST -> Root / "pics" / KeyParam(key) / "delete" =>
      removeKey(key, Reverse.list, req)
    case req @ POST -> Root / "pics" / KeyParam(key) =>
      jsonOrForm[AccessLevel](req): (access, user) =>
        changeAccess(key, access.access, user, req)
    case req @ DELETE -> Root / "pics" / KeyParam(key) =>
      removeKey(key, Reverse.drop, req)
    case req @ POST -> Root / "sync" =>
      val adminUser = PicOwner("malliina123@gmail.com")
      authed(req): user =>
        if user.name == adminUser then
          handler.originals.storage
            .load(0, 1000000)
            .flatMap: keys =>
              log.info(s"Syncing ${keys.length} keys...")
              keys
                .traverse: key =>
                  db.putMetaIfNotExists(key.withUser(adminUser))
                .flatMap: changes =>
                  log.info(s"Sync complete. Upserted ${changes.sum} rows.")
                  SeeOther(Location(Reverse.drop))
        else unauthorized(Errors("Admin required."), req)
    case req @ GET -> Root / "sockets" =>
      authedAll(req): user =>
        val userAgent = user.rh
          .get(ci"User-Agent")
          .map(ua => s"'${ua.head.value}'")
          .getOrElse("Unknown")
        log.info(s"Opening socket for '${user.name}' using user agent $userAgent.")
        val welcomeMessage = fs2.Stream(PicMessage.welcome(user.name, user.readOnly))
        val pings = fs2.Stream.awakeEvery[F](30.seconds).map(_ => PicMessage.ping)
        val updates = topic
          .subscribe(1000)
          .filter(_.forUser(user.name))
          .evalTap(msg => delay(log.info(s"Sending '${msg.asJson}' to ${user.name}...")))
        val toClient = (welcomeMessage ++ pings.mergeHaltBoth(updates)).map: message =>
          Text(message.asJson.noSpaces)
        val fromClient: fs2.Pipe[F, WebSocketFrame, Unit] = _.evalMap:
          case Text(message, _) => delay(log.info(message))
          case f                => delay(log.debug(s"Unknown WebSocket frame: $f"))
        sockets.build(toClient, fromClient)
    case req @ GET -> Root / "drop" =>
      authed(req): user =>
        val created: Option[PicMeta] = None
        val feedback = req.feedbackAs[UserFeedback]
        csrfOk: token =>
          html.drop(created, feedback, user, token)
        .clearFeedback
    case GET -> Root / "sign-up" =>
      ok(html.signUp())
    case req @ GET -> Root / "sign-in" =>
      val reverseSocial = ReverseSocial
      req.cookies
        .find(_.name == cookieNames.provider)
        .flatMap: cookie =>
          AuthProvider.forString(cookie.content).toOption
        .map:
          case Google    => reverseSocial.google
          case Microsoft => reverseSocial.microsoft
          case Facebook  => reverseSocial.facebook
          case GitHub    => reverseSocial.github
          case Amazon    => reverseSocial.amazon
          case Twitter   => reverseSocial.twitter
          case Apple     => reverseSocial.apple
        .map(r => TemporaryRedirect(Location(r.start)))
        .getOrElse(ok(html.signIn(None)))
    case req @ GET -> Root / "sign-in" / AuthProvider(id) =>
      id match
        case Twitter =>
          val twitter = socials.twitter
          twitter
            .requestToken(Urls.hostOnly(req) / reverseSocial.twitter.callback.renderString)
            .flatMap: e =>
              // add requesttoken to session
              e.fold(
                err => unauthorized(Errors(err.message), req),
                token =>
                  seeOther(Uri.unsafeFromString(twitter.authTokenUrl(token).url)).map: res =>
                    auth.withSession(TwitterState(token), req, res)
              )
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
    case req @ POST -> Root / "sign-in" / "callbacks" / "apple" =>
      handleAppleCallback(req)
    case req @ GET -> Root / "sign-in" / "callbacks" / AuthProvider(id) =>
      id match
        case Twitter =>
          val params = req.uri.query.params
          val maybe = for
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
          yield socials.twitter
            .validateTwitterCallback(token, requestToken.requestToken, verifier)
            .flatMap: e =>
              e.flatMap(_.email.toRight(OAuthError("Email missing.")))
                .fold(err => unauthorized(Errors(err.message), req), ok => userResult(ok, id, req))
          maybe.fold(
            err =>
              log.warn(s"$err in $req")
              unauthorized(Errors(s"Invalid callback parameters."), req)
            ,
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
              socials.amazon
                .validateCallback(cb)
                .map(e => e.map(user => Email(user.username.value)))
          )
        case Facebook =>
          handleCallbackV(socials.facebook, reverseSocial.facebook, req, id)
        case Apple =>
          handleCallbackV(socials.apple, reverseSocial.apple, req, id)
    case req @ GET -> Root / "sign-out" / "leave" =>
      seeOther(Reverse.signOutCallback).map: res =>
        auth.clearSession(req, res)
    case GET -> Root / "sign-out" =>
      seeOther(Reverse.list).withFeedback(UserFeedback.success("You have now logged out."))
    case GET -> Root / "legal" / "privacy" =>
      ok(html.privacyPolicy)
    case GET -> Root / "support" =>
      ok(html.support)
    case req @ GET -> Root / ".well-known" / "apple-developer-domain-association.txt" =>
      StaticFile
        .fromResource(
          "apple-developer-domain-association.txt",
          Option(req),
          preferGzipped = true
        )
        .fold(notFound(req))(_.putHeaders(cached(1.hour)).pure[F])
        .flatten
    case req @ GET -> Root / KeyParam(key) / "small" =>
      sendPic(key, PicSize.Small, req)
    case req @ GET -> Root / KeyParam(key) / "medium" =>
      sendPic(key, PicSize.Medium, req)
    case req @ GET -> Root / KeyParam(key) / "large" =>
      sendPic(key, PicSize.Large, req)
    case req @ GET -> Root / rest if ContentType.parse(rest).exists(_.isImage) =>
      PicSize(req)
        .map: size =>
          sendPic(Key(rest), size, req)
        .recover: err =>
          badRequest(Errors(NonEmptyList.of(err)))
  }

  private def csrfOk[A](content: CSRFToken => A)(using EntityEncoder[F, A]) =
    csrf
      .generateToken[F]
      .flatMap: token =>
        ok(content(CSRFUtils.toToken(token))).map: res =>
          csrf.embedInResponseCookie(res, token)

  private def sendPic(key: Key, size: PicSize, req: Request[F]) =
    val sendPic = handler(size).storage
      .find(key)
      .flatMap: maybeFile =>
        maybeFile
          .map: file =>
            StaticFile
              .fromPath(file.file, Option(req))
              .map(_.putHeaders(cached(365.days)))
              .getOrElseF(notFound(req))
          .getOrElse:
            notFoundWith(s"Not found: '$key'.")
    service.db
      .meta(key)
      .flatMap: meta =>
        if meta.access == Access.Public then sendPic
        else
          authed(req): user =>
            if meta.owner == user.name then sendPic
            else
              log.warn(
                s"User '${user.name}' not authorized to view '$key' owned by '${meta.owner}'."
              )
              unauthorized(Errors(s"Unauthorized."), req)

  private def changeAccess(key: Key, to: Access, user: PicRequest, req: Request[F]) =
    if user.readOnly then
      delay(
        log.warn(s"User '${user.name}' is nota authorized to change access of any images.")
      ).flatMap: _ =>
        unauthorized(Errors(s"Unauthorized."), req)
    else
      db.modify(key, user.name, to)
        .flatMap: meta =>
          renderRanged(req)(
            json = Accepted(Json.obj("message" -> "ok".asJson), noCache),
            html = SeeOther(Location(Reverse.list))
          )

  private def removeKey(key: Key, redir: Uri, req: Request[F]) =
    authed(req): user =>
      if user.readOnly then
        delay(log.warn(s"User '${user.name}' is not authorized to delete '$key'.")).flatMap: _ =>
          unauthorized(Errors(s"Unauthorized."), req)
      else
        db.remove(key, user.name)
          .flatMap: wasDeleted =>
            if wasDeleted then
              log.info(s"Key '$key' removed by '${user.name}' from '${req.uri}'.")
              handler
                .remove(key)
                .flatMap: _ =>
                  topic
                    .publish1(PicMessage.RemovedMessage(PicsRemoved(Seq(key)), user.name))
                    .flatMap: _ =>
                      renderRanged(req)(
                        json = Accepted(Json.obj("message" -> "ok".asJson), noCache),
                        html = seeOther(redir)
                          .withFeedback(UserFeedback.success(s"Removed '$key'."))
                      )
            else
              val msg = s"Key not found: '$key'."
              log.warn(msg)
              renderRanged(req)(
                json = keyNotFound(key),
                html = seeOther(redir).withFeedback(UserFeedback.error(msg))
              )

  private def start(validator: FlowStart[F], reverse: SocialRoute, req: Request[F]) =
    validator
      .start(Urls.hostOnly(req) / reverse.callback.renderString, Map.empty)
      .flatMap: s =>
        startLoginFlow(s, req)

  private def startHinted(
    provider: AuthProvider,
    reverse: SocialRoute,
    validator: LoginHint[F],
    req: Request[F]
  ): F[Response[F]] = delay:
    val redirectUrl = Urls.hostOnly(req) / reverse.callback.renderString
    val lastIdCookie = req.cookies.find(_.name == cookieNames.lastId)
    val promptValue = req.cookies
      .find(_.name == cookieNames.prompt)
      .map(_.content)
      .orElse(Option(SelectAccount).filter(_ => lastIdCookie.isEmpty))
    val extra = promptValue.map(c => Map(PromptKey -> c)).getOrElse(Map.empty)
    val maybeEmail = lastIdCookie.map(_.content).filter(_ => extra.isEmpty)
    maybeEmail.foreach: hint =>
      log.info(s"Starting OAuth flow with $provider using login hint '$hint'...")
    promptValue.foreach: prompt =>
      log.info(s"Starting OAuth flow with $provider using prompt '$prompt'...")
    (redirectUrl, maybeEmail, extra)
  .flatMap: (redirectUrl, maybeEmail, extra) =>
    validator
      .startHinted(redirectUrl, maybeEmail, extra)
      .flatMap: s =>
        startLoginFlow(s, req)

  private def startLoginFlow(s: Start, req: Request[F]): F[Response[F]] = delay:
    val state = randomString()
    val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map: (k, v) =>
      k -> Utils.urlEncode(v)
    val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
    log.info(s"Redirecting to '$url' with state '$state'...")
    val sessionParams = Seq(State -> state) ++ s.nonce
      .map(n => Seq(Nonce -> n))
      .getOrElse(Nil)
    (url, sessionParams)
  .flatMap: (url, sessionParams) =>
    SeeOther(Location(Uri.unsafeFromString(url.url))).map: res =>
      val session = sessionParams.toMap.asJson
      auth
        .withSession(session, req, res)
        .putHeaders(noCache)

  private def handleCallbackD(
    validator: DiscoveringAuthFlow[F, Email],
    reverse: SocialRoute,
    req: Request[F],
    provider: AuthProvider
  ): F[Response[F]] =
    handleCallback(
      reverse,
      req,
      provider,
      cb => validator.validateCallback(cb).map(e => e.flatMap(validator.parse))
    )

  private def handleCallbackV(
    validator: CallbackValidator[F, Email],
    reverse: SocialRoute,
    req: Request[F],
    provider: AuthProvider
  ): F[Response[F]] =
    handleCallback(reverse, req, provider, cb => validator.validateCallback(cb))

  private def handleCallback(
    reverse: SocialRoute,
    req: Request[F],
    provider: AuthProvider,
    validate: Callback => F[Either[AuthError, Email]]
  ): F[Response[F]] =
    val params = req.uri.query.params
    val session = auth.session[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
    val cb = Callback(
      params.get(OAuthKeys.State),
      session.get(State),
      params.get(OAuthKeys.CodeKey),
      session.get(Nonce),
      Urls.hostOnly(req) / reverse.callback.renderString
    )
    validate(cb).flatMap: e =>
      e.fold(
        err => unauthorized(Errors(err.message), req),
        email => userResult(email, provider, req)
      )

  private def handleAppleCallback(
    req: Request[F]
  )(using decoder: EntityDecoder[F, UrlForm]): F[Response[F]] =
    decoder
      .decode(req, strict = false)
      .foldF(
        failure => unauthorized(Errors(failure.message), req),
        urlForm =>
          AppleResponse(urlForm)
            .map: form =>
              val session =
                auth.session[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
              val actualState = form.state
              val sessionState = session.get(State)
              if sessionState.contains(actualState) then
                val redirectUrl = Urls.hostOnly(req) / reverseSocial.apple.callback.renderString
                socials.apple
                  .validate(form.code, redirectUrl, session.get(Nonce))
                  .flatMap: e =>
                    e.fold(
                      err => unauthorized(Errors(err.message), req),
                      email => userResult(email, Apple, req)
                    )
              else
                val detailed =
                  sessionState.fold(s"Got '$actualState' but found nothing to compare to."):
                    expected => s"Got '$actualState' but expected '$expected'."
                log.error(s"Authentication failed, state mismatch. $detailed $req")
                unauthorized(Errors("State mismatch."), req)
            .recover: err =>
              unauthorized(Errors(err), req)
      )

  private def userResult(
    email: Email,
    provider: AuthProvider,
    req: Request[F]
  ): F[Response[F]] =
    val returnUri: Uri = req.cookies
      .find(_.name == cookieNames.returnUri)
      .flatMap(c => Uri.fromString(c.content).toOption)
      .getOrElse(Reverse.list)
    SeeOther(Location(returnUri)).map: res =>
      auth.withPicsUser(UserPayload.email(email), provider, req, res)

  private def stringify(map: Map[String, String]): String =
    map.map((key, value) => s"$key=$value").mkString("&")

  private def authed(req: Request[F])(code: PicRequest => F[Response[F]]): F[Response[F]] =
    auth.authenticate(req.headers).flatMap(_.fold(identity, code))

  private def authedAll(req: Request[F])(code: PicRequest => F[Response[F]]): F[Response[F]] =
    auth.authenticateAll(req.headers).flatMap(_.fold(identity, code))

//  private def render[A: Encoder, B](req: Request[F])(json: A, html: B)(implicit
//    w: EntityEncoder[F, B]
//  ): F[Response[F]] =
//    renderRanged[A, B](req)(ok(json.asJson), ok(html))

  private def renderRanged(
    req: Request[F]
  )(json: F[Response[F]], html: F[Response[F]]): F[Response[F]] =
    val rs = ranges(req.headers)
    val qp = req.uri.query.params
    if qp.get("f").contains("json") || qp.contains("json") then json
    else if rs.exists(_.satisfies(MediaType.text.html)) then html
    else if rs.exists(_.satisfies(version10)) then json
    else if rs.exists(_.satisfies(MediaType.application.json)) then json
    else NotAcceptable(Errors("Not acceptable.").asJson, noCache)

  @unused
  private def failResize(error: ImageFailure, by: PicRequest): F[Response[F]] = error match
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

  private def jsonOrForm[T: Decoder](req: Request[F])(
    code: (T, PicRequest) => F[Response[F]]
  )(using formDecoder: EntityDecoder[F, T]): F[Response[F]] =
    authed(req): user =>
      log.info(s"Handling JSON or form data for ${req.uri}...")

      val decoder = jsonOf[F, T].orElse(formDecoder)
      decoder
        .decode(req, strict = false)
        .rethrowT
        .flatMap: t =>
          code(t, user)
        .handleErrorWith: err =>
          log.error(s"Form failure. $err")
          badRequest(Errors("Invalid form input."))

  private def badRequestWith(message: String) = badRequest(Errors(message))
  private def unauthorized(errors: Errors, req: Request[F]): F[Response[F]] = Unauthorized(
    `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
    errors.asJson
  ).map(r => auth.clearSession(req, r.removeCookie(cookieNames.provider)))
  private def keyNotFound(key: Key) = notFoundWith(s"Not found: '$key'.")
  private def notFoundWith(message: String) = NotFound(Errors(message).asJson, noCache)
  private def serverError(message: String) =
    InternalServerError(Errors(message).asJson, noCache)
  private def delay[A](thunk: => A): F[A] = F.delay(thunk)
