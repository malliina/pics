package com.malliina.pics.http4s

import cats.data.Kleisli
import cats.effect.*
import cats.effect.std.Dispatcher
import com.comcast.ip4s.{Port, host, port}
import com.malliina.database.DoobieDatabase
import com.malliina.http.CSRFConf
import com.malliina.http.io.HttpClientIO
import com.malliina.http4s.CSRFUtils
import com.malliina.logback.AppLogging
import com.malliina.pics.db.PicsDatabase
import com.malliina.pics.{BuildInfo, MultiSizeHandler, PicsConf}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import fs2.io.net.Network
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{CSRF, GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server}
import org.http4s.{HttpApp, HttpRoutes, Request, Response}

import scala.concurrent.duration.{Duration, DurationInt}

object PicsServer extends IOApp with AppResources:
  override def runtimeConfig =
    super.runtimeConfig.copy(cpuStarvationCheckInitialDelay = Duration.Inf)
  AppLogging.init()

  override def run(args: List[String]): IO[ExitCode] =
    for
      conf <- PicsConf.loadF[IO]
      app <- server[IO](conf, MultiSizeHandler.default).use(_ => IO.never).as(ExitCode.Success)
    yield app

trait AppResources:
  private val log = AppLogger(getClass)

  private val serverPort: Port =
    sys.env.get("SERVER_PORT").flatMap(s => Port.fromString(s)).getOrElse(port"9000")

  def server[F[_]: Async](
    conf: => PicsConf,
    sizeHandler: Resource[F, MultiSizeHandler[F]],
    port: Port = serverPort
  ): Resource[F, Server] =
    val csrfConf = CSRFConf.default
    val csrfUtils = CSRFUtils(csrfConf)
    for
      handler <- sizeHandler
      csrf <- Resource.eval(csrfUtils.default[F](onFailure = CSRFUtils.defaultFailure[F]))
      picsApp <- appResource(conf, handler, csrf, csrfConf)
      _ = log.info(s"Binding on port $port using app version ${BuildInfo.gitHash}...")
      server <- EmberServerBuilder
        .default[F]
        .withHost(host"0.0.0.0")
        .withPort(serverPort)
        .withHttpWebSocketApp(sockets => app[F](picsApp, sockets, csrfUtils.middleware(csrf)))
        .withErrorHandler(ErrorHandler[F].partial)
        .withIdleTimeout(60.minutes)
        .withRequestHeaderReceiveTimeout(30.minutes)
        .withShutdownTimeout(1.millis)
        .build
    yield server

  private def appResource[F[_]: Async](
    conf: => PicsConf,
    handler: MultiSizeHandler[F],
    csrf: CSRF[F, F],
    csrfConf: CSRFConf
  ): Resource[F, PicsService[F]] = for
    dispatcher <- Dispatcher.parallel[F]
    http <- HttpClientIO.resource
    _ <- AppLogging.resource(dispatcher, http)
    topic <- Resource.eval(Topic[F, PicMessage])
    doobieDatabase <-
      val db = conf.db
      log.info(s"Using database ${db.url}...")
      if conf.isFull then DoobieDatabase.init[F](db)
      else Resource.eval(DoobieDatabase.fast(db))
  yield
    val db = PicsDatabase(doobieDatabase)
    PicsService.default(conf, db, topic, handler, http, csrf, csrfConf)

  def app[F[_]: Async](
    svc: PicsService[F],
    sockets: WebSocketBuilder2[F],
    csrfChecker: CSRFUtils.CSRFChecker[F]
  ): HttpApp[F] =
    csrfChecker:
      GZip:
        HSTS:
          orNotFound:
            Router(
              "/" -> svc.routes(sockets),
              "/assets" -> StaticService[F].routes
            )

  private def orNotFound[F[_]: Async](rs: HttpRoutes[F]): Kleisli[F, Request[F], Response[F]] =
    Kleisli(req => rs.run(req).getOrElseF(PicsBasicService[F].notFound(req)))
