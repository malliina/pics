package com.malliina.pics.http4s

import cats.data.Kleisli
import cats.effect.kernel.Temporal
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.malliina.pics.db.{DoobieDatabase, PicsDatabase}
import com.malliina.pics.{BuildInfo, MultiSizeHandlerIO, PicsConf}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server}
import org.http4s.{HttpRoutes, Request, Response}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object PicsServer extends IOApp:
  type AppService = Kleisli[IO, Request[IO], Response[IO]]

  private val log = AppLogger(getClass)

  val defaultPort = 9000

  def server(
    conf: PicsConf,
    sizeHandler: IO[MultiSizeHandlerIO] = MultiSizeHandlerIO.default(),
    port: Int = defaultPort
  ): Resource[IO, Server] = for
    handler <- Resource.eval(sizeHandler)
    picsApp <- appResource(conf, handler)
    _ = log.info(s"Binding on port $port using app version ${BuildInfo.hash}...")
    server <- BlazeServerBuilder[IO]
      .bindHttp(port = port, "0.0.0.0")
      .withHttpWebSocketApp(sockets => app(picsApp, sockets))
      .withIdleTimeout(60.minutes)
      .withResponseHeaderTimeout(30.minutes)
      .withServiceErrorHandler(ErrorHandler[IO, IO])
      .withBanner(Nil)
      .resource
  yield server

  def appResource(conf: PicsConf, handler: MultiSizeHandlerIO)(implicit
    t: Temporal[IO]
  ): Resource[IO, PicsService] = for
    topic <- Resource.eval(Topic[IO, PicMessage])
    tx <- DoobieDatabase.migratedResource(conf.db)
  yield
    val db = PicsDatabase(DoobieDatabase(tx))
//    val csrf =
//      CSRF.generateSigningKey[IO].map { key =>
//        CSRF[IO, IO](key, _ => true)
//          .withOnFailure(Unauthorized(Json.toJson(Errors.single("CSRF failure."))))
//      }
    PicsService(conf, db, topic, handler, t)

  def app(svc: PicsService, sockets: WebSocketBuilder2[IO])(implicit t: Temporal[IO]): AppService =
    GZip {
      HSTS {
        orNotFound {
          Router(
            "/" -> svc.routes(sockets),
            "/assets" -> StaticService[IO]().routes
          )
        }
      }
    }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(PicsConf.unsafeLoad()).use(_ => IO.never).as(ExitCode.Success)
