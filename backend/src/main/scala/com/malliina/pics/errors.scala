package com.malliina.pics

import java.io.IOException
import java.nio.file.Path

import cats.data.NonEmptyList
import com.malliina.values.ErrorMessage
import com.malliina.web.JWTError
import com.sksamuel.scrimage.ImageParseException
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait ImageFailure

case class ImageException(ioe: IOException) extends ImageFailure
case class UnsupportedFormat(format: String, supported: Seq[String]) extends ImageFailure
case class ImageReaderFailure(file: Path) extends ImageFailure
case class ResizeException(ipe: ImageParseException) extends ImageFailure

class KeyNotFound(key: Key) extends Exception(s"Key not found: '$key'.")

case class SingleError(message: String, key: String)

object SingleError {
  implicit val json: Codec[SingleError] = deriveCodec[SingleError]

  def apply(message: String): SingleError = apply(message, "generic")

  def forJWT(error: JWTError): SingleError =
    SingleError(error.message.message, error.key)
}

case class Errors(errors: NonEmptyList[SingleError])

object Errors {
  implicit val se: Codec[SingleError] = SingleError.json
  import cats.implicits._
//  implicit def nelJson[T: Format]: Format[NonEmptyList[T]] =
//    Format(
//      Reads { json =>
//        json
//          .validate[List[T]]
//          .flatMap(_.toNel.map(t => JsSuccess(t)).getOrElse(JsError(s"Empty list: '$json'.")))
//      },
//      Writes.list[T].contramap(_.toList)
//    )

  implicit val json: Codec[Errors] = deriveCodec[Errors]

  def apply(message: ErrorMessage): Errors = Errors.single(message.message)
  def single(message: String): Errors = Errors(NonEmptyList.of(SingleError(message)))
}
