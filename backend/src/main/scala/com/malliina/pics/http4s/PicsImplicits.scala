package com.malliina.pics.http4s

import cats.effect.Sync
import com.malliina.http4s.{BasicService, CSRFSupport}
import org.http4s.headers.`Content-Type`
import org.http4s.{Charset, EntityEncoder, MediaType}
import scalatags.generic.Frag

trait MyScalatagsInstances:
  given scalatagsEncoder[F[_], C <: Frag[?, String]](using
    charset: Charset = Charset.`UTF-8`
  ): EntityEncoder[F, C] =
    contentEncoder(MediaType.text.html, charset)

  private def contentEncoder[F[_], C <: Frag[?, String]](
    mediaType: MediaType,
    charset: Charset
  ): EntityEncoder[F, C] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[C](content => content.render)
      .withContentType(`Content-Type`(mediaType, charset))

class PicsBasicService[F[_]: Sync] extends BasicService[F] with MyScalatagsInstances:
  export BasicService.noCache

abstract class HtmlService[F[_]: Sync] extends PicsBasicService[F] with CSRFSupport[F]
