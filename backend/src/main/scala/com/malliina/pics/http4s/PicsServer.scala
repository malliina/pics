package com.malliina.pics.http4s

import cats.data.Kleisli
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.effect.kernel.Temporal
import com.malliina.pics.BuildInfo
import com.malliina.pics.db.{DoobieDatabase, PicsDatabase}
import com.malliina.pics.{MultiSizeHandlerIO, PicsConf}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import org.http4s.server.{Router, Server}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.{HttpRoutes, Request, Response}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object PicsServer extends IOApp:
  type AppService = Kleisli[IO, Request[IO], Response[IO]]

  val log = AppLogger(getClass)

  val port = 9000

  def server(conf: PicsConf): Resource[IO, Server] = for
    handler <- Resource.eval(MultiSizeHandlerIO.default())
    picsApp <- appResource(conf, handler)
    _ = log.info(s"Binding on port $port using app version ${BuildInfo.hash}...")
    server <- BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port = port, "0.0.0.0")
      .withHttpApp(picsApp)
      .withIdleTimeout(60.minutes)
      .withResponseHeaderTimeout(30.minutes)
      .withServiceErrorHandler(ErrorHandler[IO, IO])
      .resource
  yield server

  def appResource(conf: PicsConf, handler: MultiSizeHandlerIO) = for
    topic <- Resource.eval(Topic[IO, PicMessage])
    tx <- DoobieDatabase.migratedResource(conf.db)
  yield
    val db = PicsDatabase(DoobieDatabase(tx))
//    val csrf =
//      CSRF.generateSigningKey[IO].map { key =>
//        CSRF[IO, IO](key, _ => true)
//          .withOnFailure(Unauthorized(Json.toJson(Errors.single("CSRF failure."))))
//      }
    app(conf, db, handler, topic)

  private def app(
    conf: PicsConf,
    db: PicsDatabase[IO],
    handler: MultiSizeHandlerIO,
    topic: Topic[IO, PicMessage]
  )(implicit t: Temporal[IO]) = GZip {
    HSTS {
      orNotFound {
        Router(
          "/" -> PicsService(conf, db, topic, handler, t).routes,
          "/assets" -> StaticService[IO]().routes
        )
      }
    }
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(PicsConf.unsafeLoad()).use(_ => IO.never).as(ExitCode.Success)
