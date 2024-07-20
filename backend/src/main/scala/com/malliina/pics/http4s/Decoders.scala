package com.malliina.pics.http4s

import cats.effect.Concurrent
import com.malliina.http.Errors
import com.malliina.http4s.FormReadableT
import org.http4s.{DecodeResult, EntityDecoder, MalformedMessageBodyFailure, UrlForm}

trait Decoders[F[_]: Concurrent]:
  given [T](using reader: FormReadableT[T]): EntityDecoder[F, T] =
    EntityDecoder[F, UrlForm].flatMapR: form =>
      toDecodeResult(reader.read(form))

  private def toDecodeResult[T](e: Either[Errors, T]): DecodeResult[F, T] = e.fold(
    errors => DecodeResult.failureT(MalformedMessageBodyFailure(errors.message.message)),
    ok => DecodeResult.successT(ok)
  )
