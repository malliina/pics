package com.malliina.pics.http4s

import cats.Applicative
import cats.effect.IO
import com.malliina.pics.Errors
import org.http4s.*
import io.circe.syntax.EncoderOps
import org.http4s.headers.`Cache-Control`
import org.http4s.CacheDirective.{`must-revalidate`, `no-cache`, `no-store`}

object BasicService extends BasicService[IO]

trait BasicService[F[_]] extends PicsImplicits[F]:
  val noCache: `Cache-Control` = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)

  def serverError(implicit a: Applicative[F]): F[Response[F]] =
    InternalServerError(Errors.single(s"Server error.").asJson, noCache)

  def notFound(req: Request[F])(implicit a: Applicative[F]): F[Response[F]] =
    NotFound(Errors.single(s"Not found: '${req.uri}'.").asJson, noCache)
