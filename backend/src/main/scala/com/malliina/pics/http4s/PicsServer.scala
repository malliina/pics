package com.malliina.pics.http4s

import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.effect.*
import cats.effect.std.Dispatcher
import com.comcast.ip4s.{Port, host, port}
import com.malliina.database.DoobieDatabase
import com.malliina.http.io.HttpClientIO
import com.malliina.logback.AppLogging
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.{BuildInfo, CSRFConf, MultiSizeHandler, PicsConf}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{CSRF, GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Middleware, Router, Server}
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
    csrf <- Resource.eval(MyCSRF.build[IO, IO](FunctionK.id[IO]))
    picsApp <- appResource(conf, handler, csrf)
    _ = log.info(s"Binding on port $port using app version ${BuildInfo.gitHash}...")
    server <- EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(serverPort)
      .withHttpWebSocketApp(sockets => app(picsApp, sockets, csrf))
      .withErrorHandler(ErrorHandler[IO].partial)
      .withIdleTimeout(60.minutes)
      .withRequestHeaderReceiveTimeout(30.minutes)
      .withShutdownTimeout(1.millis)
      .build
  yield server

  private def appResource[F[_]: Async](
    conf: => PicsConf,
    handler: MultiSizeHandler[F],
    csrf: CSRF[F, F]
  ): Resource[F, PicsService[F]] = for
    dispatcher <- Dispatcher.parallel[F]
    http <- HttpClientIO.resource
    _ <- AppLogging.resource(dispatcher, http)
    topic <- Resource.eval(Topic[F, PicMessage])
    doobieDatabase <-
      if conf.isFull then DoobieDatabase.init[F](conf.db)
      else Resource.eval(DoobieDatabase.fast(conf.db))
  yield
    val db = PicsDatabase(doobieDatabase)
    PicsService.default(conf, db, topic, handler, http, csrf)

  def app[F[_]: Async](
    svc: PicsService[F],
    sockets: WebSocketBuilder2[F],
    csrf: CSRF[F, F]
  ): AppService[F] =
    val csrfHandler: Middleware[F, Request[F], Response[F], Request[F], Response[F]] = http =>
      Kleisli: (r: Request[F]) =>
        val nocheck =
          r.headers
            .get(CSRFConf.CsrfHeaderName)
            .map(_.head.value)
            .contains(CSRFConf.CsrfTokenNoCheck)
        val response = http(r)
        if nocheck then response
        else if r.method.isSafe then response
        else csrf.checkCSRF(r, response)
    csrfHandler:
      GZip:
        HSTS:
          orNotFound:
            Router(
              "/" -> svc.routes(sockets),
              "/assets" -> StaticService[F].routes
            )

  private def orNotFound[F[_]: Async](rs: HttpRoutes[F]): Kleisli[F, Request[F], Response[F]] =
    Kleisli(req => rs.run(req).getOrElseF(PicsBasicService[F].notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    for
      conf <- PicsConf.loadF[IO]
      app <- server(conf).use(_ => IO.never).as(ExitCode.Success)
    yield app
