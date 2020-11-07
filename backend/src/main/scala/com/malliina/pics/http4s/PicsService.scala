package com.malliina.pics.http4s

import _root_.play.api.libs.json.{Json, Writes}
import cats.data.NonEmptyList
import cats.effect._
import com.malliina.pics.auth.{Http4sAuth, UserPayload}
import com.malliina.pics.html.PicsHtml
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
import org.http4s.CacheDirective.{`max-age`, `must-revalidate`, `no-cache`, `no-store`, `public`}
import org.http4s._
import org.http4s.headers.{Accept, Location, `Cache-Control`, `WWW-Authenticate`}
import org.slf4j.LoggerFactory
import PicsImplicits._
import com.malliina.html.UserFeedback
import com.malliina.http.OkClient
import com.malliina.play.auth.AuthValidator.Callback
import com.malliina.play.auth.OAuthKeys.{Nonce, State}
import com.malliina.play.auth.{AuthValidator, CodeValidator, OAuthKeys}
import controllers.Social
import controllers.Social._

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
            render(ranges)(json = Pics(entries), html = {
              val feedback = None // UserFeedbacks.flashed(req.rh.flash)
              html.pics(entries, feedback, listRequest.user).tags
            })
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
                  .requestToken(Urls.hostOnly(req) / ReverseSocial.twitter.callback.renderString)
              )
            )
            .flatMap { e =>
              e.fold(
                err => unauthorized(Errors(err.message)),
                token => SeeOther(Location(Uri.unsafeFromString(twitter.authTokenUrl(token).url)))
              )
            }
        case Google =>
          val redirectUrl = Urls.hostOnly(req) / ReverseSocial.google.callback.renderString
          val google = socials.google
          IO.fromFuture(IO(google.start(redirectUrl, Map.empty))).flatMap { s =>
            val state = CodeValidator.randomString()
            val encodedParams = (s.params ++ Map(OAuthKeys.State -> state)).map {
              case (k, v) => k -> AuthValidator.urlEncode(v)
            }
            val url = s.authorizationEndpoint.append(s"?${stringify(encodedParams)}")
            val sessionParams = Seq(State -> state) ++ s.nonce
              .map(n => Seq(Nonce -> n))
              .getOrElse(Nil)
            // Writes sessionParams to session cookie, redirects to url with no-cache headers
            SeeOther(Location(Uri.unsafeFromString(url.url))).map { res =>
              val session = Json.toJson(sessionParams.toMap)
              auth
                .withSession(session, res)
                .putHeaders(noCache)
            }
          }
        case other =>
          badRequest(Errors.single(s"Unsupported provider: '$other'."))
      }
    case req @ GET -> Root / "sign-in" / AuthProvider(id) / "callback" =>
      id match {
        case Google =>
          val params = req.uri.query.params
          val session = auth.session[Map[String, String]](req.headers).toOption.getOrElse(Map.empty)
          val cb = Callback(
            params.get(OAuthKeys.CodeKey),
            session.get(State),
            params.get(OAuthKeys.CodeKey),
            session.get(Nonce),
            Urls.hostOnly(req) / ReverseSocial.google.callback.renderString
          )
          IO.fromFuture(IO(socials.google.validateCallback(cb))).flatMap { e =>
            e.flatMap(socials.google.parse)
              .fold(
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
        case other =>
          badRequest(Errors.single("Not supported yet."))
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
    `WWW-Authenticate`(NonEmptyList.of(Challenge("mysceme", "myrealm"))),
    Json.toJson(errors)
  ).map(_.removeCookie(ProviderCookie)).map { r =>
    println(s"removing $ProviderCookie")
    r
  }
  def notFound(req: Request[IO]) = notFoundWith(s"Not found: '${req.uri}'.")
  def notFoundWith(message: String) = NotFound(Json.toJson(Errors.single(message)))
}
