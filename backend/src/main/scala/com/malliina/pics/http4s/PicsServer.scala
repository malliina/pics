package com.malliina.pics.http4s

import cats.data.Kleisli
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import com.malliina.http.OkClient
import com.malliina.pics.db.{DoobieDatabase, DoobieDatabase2, DoobiePicsDatabase}
import com.malliina.pics.{MetaSourceT, PicsConf}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{HttpRoutes, Request, Response}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object PicsServer extends IOApp {
  type AppService = Kleisli[IO, Request[IO], Response[IO]]

  val log = LoggerFactory.getLogger(getClass)

  def server(conf: PicsConf) = for {
    picsApp <- appResource(conf)
    server <- BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port = 9000, "0.0.0.0")
      .withHttpApp(picsApp)
      .resource
  } yield server

  def appResource(conf: PicsConf) = for {
    blocker <- Blocker[IO]
    tx <- DoobieDatabase.migratedResource(conf.db, blocker)
  } yield {
    val db = DoobiePicsDatabase(new DoobieDatabase2(tx))
    app(conf, db, blocker)
  }

  private def app(conf: PicsConf, db: MetaSourceT[IO], blocker: Blocker) = orNotFound {
    Router(
      "/" -> PicsService(conf, db, blocker, contextShift).routes,
      "/assets" -> StaticService(blocker, contextShift).routes
    )
  }

  def orNotFound(rs: HttpRoutes[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli(req => rs.run(req).getOrElseF(BasicService.notFound(req)))

  override def run(args: List[String]): IO[ExitCode] =
    server(PicsConf.load).use(_ => IO.never).as(ExitCode.Success)
}
