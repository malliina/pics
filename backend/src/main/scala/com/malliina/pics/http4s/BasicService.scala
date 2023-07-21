package com.malliina.pics.http4s

import cats.Applicative
import com.malliina.pics.Errors
import io.circe.syntax.EncoderOps
import org.http4s.*
import org.http4s.CacheDirective.{`must-revalidate`, `no-cache`, `no-store`}
import org.http4s.headers.`Cache-Control`

class BasicService[F[_]] extends PicsImplicits[F]:
  val noCache: `Cache-Control` = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)

  def serverError(implicit a: Applicative[F]): F[Response[F]] =
    InternalServerError(Errors(s"Server error.").asJson, noCache)

  def notFound(req: Request[F])(implicit a: Applicative[F]): F[Response[F]] =
    NotFound(Errors(s"Not found: '${req.uri}'.").asJson, noCache)
