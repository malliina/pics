package com.malliina.pics.http4s

import cats.data.Kleisli
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import com.malliina.pics.db.{DoobieDatabase, DoobieDatabase2, DoobiePicsDatabase}
import com.malliina.pics.{MetaSourceT, PicsConf}
import fs2.concurrent.Topic
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.HSTS
import org.http4s.{HttpRoutes, Request, Response}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object PicsServer extends IOApp {
  type AppService = Kleisli[IO, Request[IO], Response[IO]]

  val log = LoggerFactory.getLogger(getClass)

  val port = 9000

  def server(conf: PicsConf) = for {
    picsApp <- appResource(conf)
    _ = log.info(s"Binding on port $port...")
    server <- BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port = port, "0.0.0.0")
      .withHttpApp(picsApp)
      .resource
  } yield server

  def appResource(conf: PicsConf) = for {
    blocker <- Blocker[IO]
    topic <- Resource.liftF(Topic[IO, PicMessage](PicMessage.ping))
    tx <- DoobieDatabase.migratedResource(conf.db, blocker)
  } yield {
    val db = DoobiePicsDatabase(new DoobieDatabase2(tx))
    app(conf, db, blocker, topic)
  }

  private def app(
    conf: PicsConf,
    db: MetaSourceT[IO],
    blocker: Blocker,
    topic: Topic[IO, PicMessage]
  ) = HSTS {
    orNotFound {
      Router(
        "/" -> PicsService(conf, db, topic, blocker, contextShift, timer).routes,
        "/assets" -> StaticService(blocker, contextShift).routes
      )
    }
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(PicsConf.load).use(_ => IO.never).as(ExitCode.Success)
}
