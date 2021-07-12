package com.malliina.pics.http4s

import cats.data.Kleisli
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import com.malliina.pics.BuildInfo
import com.malliina.pics.db.{DoobieDatabase, PicsDatabase}
import com.malliina.pics.{MultiSizeHandlerIO, PicsConf}
import com.malliina.util.AppLogger
import fs2.concurrent.Topic
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{GZip, HSTS}
import org.http4s.{HttpRoutes, Request, Response}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object PicsServer extends IOApp {
  type AppService = Kleisli[IO, Request[IO], Response[IO]]

  val log = AppLogger(getClass)

  val port = 9000

  def server(conf: PicsConf) = for {
    picsApp <- appResource(conf, MultiSizeHandlerIO.default())
    _ = log.info(s"Binding on port $port using app version ${BuildInfo.hash}...")
    server <- BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port = port, "0.0.0.0")
      .withHttpApp(picsApp)
      .withIdleTimeout(60.minutes)
      .resource
  } yield server

  def appResource(conf: PicsConf, handler: MultiSizeHandlerIO) = for {
    blocker <- Blocker[IO]
    topic <- Resource.eval(Topic[IO, PicMessage](PicMessage.ping))
    tx <- DoobieDatabase.migratedResource(conf.db, blocker)
  } yield {
    val db = PicsDatabase(DoobieDatabase(tx))
//    val csrf =
//      CSRF.generateSigningKey[IO].map { key =>
//        CSRF[IO, IO](key, _ => true)
//          .withOnFailure(Unauthorized(Json.toJson(Errors.single("CSRF failure."))))
//      }
    app(conf, db, handler, blocker, topic)
  }

  private def app(
    conf: PicsConf,
    db: PicsDatabase[IO],
    handler: MultiSizeHandlerIO,
    blocker: Blocker,
    topic: Topic[IO, PicMessage]
  ) = GZip {
    HSTS {
      orNotFound {
        Router(
          "/" -> PicsService(conf, db, topic, handler, blocker, contextShift, timer).routes,
          "/assets" -> StaticService(blocker, contextShift).routes
        )
      }
    }
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(PicsConf.load).use(_ => IO.never).as(ExitCode.Success)
}
