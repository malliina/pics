package com.malliina.pics.http4s

import java.nio.file.{Files, Paths}

import cats.data.NonEmptyList
import cats.effect.{Blocker, ContextShift, Sync}
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxOptionId, _}
import com.malliina.pics.http4s.StaticService.log
import com.malliina.values.UnixPath
import org.http4s.CacheDirective.{`max-age`, `no-cache`, `public`}
import org.http4s.headers.`Cache-Control`
import org.http4s.server.staticcontent.{FileService, ResourceService, fileService, resourceService}
import org.http4s.{HttpRoutes, Request, StaticFile}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt

object StaticService {
  private val log = LoggerFactory.getLogger(getClass)

  def apply[F[_]](blocker: Blocker, contextShift: ContextShift[F])(
    implicit s: Sync[F]
  ): StaticService[F] =
    new StaticService[F](blocker)(contextShift, s)
}

class StaticService[F[_]](blocker: Blocker)(implicit cs: ContextShift[F], s: Sync[F])
  extends BasicService[F] {
  val fontExtensions = List(".woff", ".woff2", ".eot", ".ttf")
  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico") ++ fontExtensions

//  val routes = resourceService[F](ResourceService.Config("/db", blocker))
//  val routes = fileService(FileService.Config("./public", blocker))
  val routes = HttpRoutes.of[F] {
    case req @ GET -> rest => // if supportedStaticExtensions.exists(req.pathInfo.endsWith) =>
      val file = UnixPath(rest.toList.mkString("/"))
      val isCacheable = file.value.count(_ == '.') == 2
      val cacheHeaders =
        if (isCacheable) NonEmptyList.of(`max-age`(365.days), `public`)
        else NonEmptyList.of(`no-cache`())
//      val path =
//        Paths
//          .get(sys.props("user.dir"))
//          .toAbsolutePath
//          .getParent
//          .resolve("assets")
//          .resolve(file.value)
//      log.info(s"Asset '${path.toAbsolutePath}'.")
      val res = s"/${file.value}"
      println(s"Res '$res'.")
      StaticFile
        .fromResource(res, blocker, Option(req))
        //      StaticFile
        //        .fromFile(path.toFile, blocker, Option(req))
        .map(_.putHeaders(`Cache-Control`(cacheHeaders)))
        //        .orElse(StaticFile.fromResource[F](file.value, blocker, req.some))
        .fold(onNotFound(req))(_.pure[F])
        .flatten
  }

  private def onNotFound(req: Request[F]) = {
    println(s"Not found '${req.uri}'.")
    notFound(req)
  }
}
