package com.malliina.pics.http4s

import cats.Applicative
import cats.effect.IO
import com.malliina.pics.Errors
import org.http4s.{Request, Response}
import play.api.libs.json.Json

object BasicService extends BasicService[IO]

trait BasicService[F[_]] extends PicsImplicits[F] {
  def notFound(req: Request[F])(implicit a: Applicative[F]): F[Response[F]] =
    NotFound(Json.toJson(Errors.single(s"Not found: '${req.uri}'.")))
}
