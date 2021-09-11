package com.malliina.pics.http4s

import cats.effect.IO
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.{Charset, DefaultCharset, EntityEncoder, MediaType, syntax}
import org.http4s.circe.CirceInstances
import scalatags.generic.Frag

trait MyScalatagsInstances {
  implicit def scalatagsEncoder[F[_], C <: Frag[?, String]](implicit
    charset: Charset = DefaultCharset
  ): EntityEncoder[F, C] =
    contentEncoder(MediaType.text.html)

  private def contentEncoder[F[_], C <: Frag[?, String]](
    mediaType: MediaType
  )(implicit charset: Charset): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))
}

abstract class PicsImplicits[F[_]]
  extends syntax.AllSyntaxBinCompat
  with Http4sDsl[F]
  with MyScalatagsInstances
  with CirceInstances

object PicsImplicits extends PicsImplicits[IO]
