package com.malliina.pics.http4s

import cats.data.Kleisli
import cats.effect.std.Dispatcher
import cats.effect.*
import com.comcast.ip4s.{Port, host, port}
import com.malliina.database.DoobieDatabase
import com.malliina.http.io.HttpClientIO
import com.malliina.logback.AppLogging
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.{BuildInfo, MultiSizeHandler, PicsConf}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server}
import org.http4s.{HttpRoutes, Request, Response}

import scala.concurrent.duration.{Duration, DurationInt}

object PicsServer extends PicsApp:
  override def runtimeConfig =
    super.runtimeConfig.copy(cpuStarvationCheckInitialDelay = Duration.Inf)
  AppLogging.init()

trait PicsApp extends IOApp:
  type AppService[F[_]] = Kleisli[F, Request[F], Response[F]]

  private val log = AppLogger(getClass)

  private val serverPort: Port =
    sys.env.get("SERVER_PORT").flatMap(s => Port.fromString(s)).getOrElse(port"9000")

  def server(
    conf: => PicsConf,
    sizeHandler: Resource[IO, MultiSizeHandler[IO]] = MultiSizeHandler.default,
    port: Port = serverPort
  ): Resource[IO, Server] = for
    handler <- sizeHandler
    picsApp <- appResource(conf, handler)
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
    handler: MultiSizeHandler[F]
  ): Resource[F, PicsService[F]] = for
    dispatcher <- Dispatcher.parallel[F]
    http <- HttpClientIO.resource
    _ <- AppLogging.resource(dispatcher, http)
    topic <- Resource.eval(Topic[F, PicMessage])
    doobieDatabase <- DoobieDatabase.init[F](conf.db)
  yield
    val db = PicsDatabase(doobieDatabase)
//    val csrf =
//      CSRF.generateSigningKey[IO].map { key =>
//        CSRF[IO, IO](key, _ => true)
//          .withOnFailure(Unauthorized(Json.toJson(Errors.single("CSRF failure."))))
//      }
    PicsService.default(conf, db, topic, handler, http)

  def app[F[_]: Async](svc: PicsService[F], sockets: WebSocketBuilder2[F]): AppService[F] =
    GZip:
      HSTS:
        orNotFound:
          Router(
            "/" -> svc.routes(sockets),
            "/assets" -> StaticService[F].routes
          )

  private def orNotFound[F[_]: Async](rs: HttpRoutes[F]): Kleisli[F, Request[F], Response[F]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService[F].notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(PicsConf.unsafeLoad()).use(_ => IO.never).as(ExitCode.Success)
