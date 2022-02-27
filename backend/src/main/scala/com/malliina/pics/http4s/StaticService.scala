package com.malliina.pics.http4s

import cats.data.NonEmptyList
import cats.effect.{Async, Sync}
import cats.implicits.*
import com.malliina.pics.BuildInfo
import com.malliina.pics.http4s.StaticService.log
import com.malliina.util.AppLogger
import com.malliina.values.UnixPath
import org.http4s.CacheDirective.{`max-age`, `no-cache`, `public`}
import org.http4s.headers.`Cache-Control`
import org.http4s.{Header, HttpRoutes, Request, StaticFile}
import org.typelevel.ci.CIStringSyntax

import java.nio.file.Files
import scala.concurrent.duration.DurationInt

object StaticService:
  private val log = AppLogger(getClass)

class StaticService[F[_]: Async] extends BasicService[F]:
  val fontExtensions = List(".woff", ".woff2", ".eot", ".ttf")
  val supportedStaticExtensions: List[String] =
    List(".html", ".js", ".map", ".css", ".png", ".ico", ".svg") ++ fontExtensions

  private val publicDir = fs2.io.file.Path(BuildInfo.assetsDir)
  private val allowAllOrigins = Header.Raw(ci"Access-Control-Allow-Origin", "*")

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ GET -> rest if supportedStaticExtensions.exists(rest.toString.endsWith) =>
      val file = UnixPath(rest.segments.mkString("/"))
      val isCacheable = file.value.count(_ == '.') == 2 || file.value.startsWith("static/")
      val cacheHeaders =
        if isCacheable then NonEmptyList.of(`max-age`(365.days), `public`)
        else NonEmptyList.of(`no-cache`())
      val assetPath: fs2.io.file.Path = publicDir.resolve(file.value)
      val resourcePath = s"${BuildInfo.publicFolder}/${file.value}"
      log.info(s"Searching for '$resourcePath' or '$assetPath'....")
      StaticFile
        .fromResource(resourcePath, Option(req))
        .orElse(StaticFile.fromPath(assetPath, Option(req)))
        .map(_.putHeaders(`Cache-Control`(cacheHeaders), allowAllOrigins))
        .fold(onNotFound(req))(_.pure[F])
        .flatten
  }

  private def onNotFound(req: Request[F]) =
    log.info(s"Not found '${req.uri}'.")
    notFound(req)
