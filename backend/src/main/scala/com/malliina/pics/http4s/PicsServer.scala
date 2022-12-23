package com.malliina.pics.http4s

import cats.data.Kleisli
import cats.effect.kernel.{Resource, Temporal}
import cats.effect.std.Dispatcher
import cats.effect.{Async, ExitCode, IO, IOApp}
import cats.syntax.all.toFunctorOps
import com.malliina.pics.db.{DoobieDatabase, PicsDatabase}
import com.malliina.pics.{BuildInfo, MultiSizeHandler, PicsConf}
import com.malliina.util.AppLogger
import com.malliina.http.io.{HttpClientF, HttpClientF2, HttpClientIO}
import com.malliina.http.HttpClient
import fs2.concurrent.Topic
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server}
import org.http4s.{HttpRoutes, Request, Response}
import com.comcast.ip4s.{Port, host, port}
import com.malliina.logback.LogstreamsUtils

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object PicsServer extends IOApp:
  type AppService = Kleisli[IO, Request[IO], Response[IO]]

  private val log = AppLogger(getClass)

  private val serverPort: Port =
    sys.env.get("SERVER_PORT").flatMap(s => Port.fromString(s)).getOrElse(port"9000")

  def server(
    conf: => PicsConf,
    sizeHandler: Resource[IO, MultiSizeHandler[IO]] = MultiSizeHandler.default,
    port: Port = serverPort
  ): Resource[IO, Server] = for
    handler <- sizeHandler
    picsApp <- appResource(conf, handler, HttpClientIO.resource)
    _ = log.info(s"Binding on port $port using app version ${BuildInfo.gitHash}...")
    server <- EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(serverPort)
      .withHttpWebSocketApp(sockets => app(picsApp, sockets))
      .withErrorHandler(ErrorHandler[IO].partial)
      .withIdleTimeout(60.minutes)
      .withRequestHeaderReceiveTimeout(30.minutes)
      .withShutdownTimeout(1.millis)
      .build
  yield server

  private def appResource[F[_]: Async](
    conf: => PicsConf,
    handler: MultiSizeHandler[F],
    httpResource: Resource[F, HttpClientF2[F]]
  ): Resource[F, PicsService[F]] = for
    topic <- Resource.eval(Topic[F, PicMessage])
    tx <- DoobieDatabase.migratedResource[F](conf.db)
    dispatcher <- Dispatcher[F]
    http <- httpResource
    _ <- Resource.eval(LogstreamsUtils.install(dispatcher, http))
  yield
    val db = PicsDatabase(DoobieDatabase(tx))
//    val csrf =
//      CSRF.generateSigningKey[IO].map { key =>
//        CSRF[IO, IO](key, _ => true)
//          .withOnFailure(Unauthorized(Json.toJson(Errors.single("CSRF failure."))))
//      }
    PicsService.default(conf, db, topic, handler, http)

  def app(svc: PicsService[IO], sockets: WebSocketBuilder2[IO])(implicit
    t: Temporal[IO]
  ): AppService =
    GZip {
      HSTS {
        orNotFound {
          Router(
            "/" -> svc.routes(sockets),
            "/assets" -> StaticService[IO].routes
          )
        }
      }
    }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(PicsConf.unsafeLoad()).use(_ => IO.never).as(ExitCode.Success)
