package com.malliina.pics

import java.io.IOException
import java.nio.file.Path

import cats.data.NonEmptyList
import com.malliina.play.auth.JWTError
import com.sksamuel.scrimage.ImageParseException
import play.api.http.Writeable
import play.api.libs.json.{Format, JsError, JsSuccess, Json, Reads, Writes}

sealed trait ImageFailure

case class ImageException(ioe: IOException) extends ImageFailure
case class UnsupportedFormat(format: String, supported: Seq[String]) extends ImageFailure
case class ImageReaderFailure(file: Path) extends ImageFailure
case class ResizeException(ipe: ImageParseException) extends ImageFailure

class KeyNotFound(key: Key) extends Exception(s"Key not found: '$key'.")

case class SingleError(message: String, key: String)

object SingleError {
  implicit val json = Json.format[SingleError]

  def apply(message: String): SingleError = apply(message, "generic")

  def forJWT(error: JWTError): SingleError =
    SingleError(error.message.message, error.key)
}

case class Errors(errors: NonEmptyList[SingleError])

object Errors {
  implicit val se = SingleError.json
  import cats.implicits._
  implicit def nelJson[T: Format]: Format[NonEmptyList[T]] =
    Format(
      Reads { json =>
        json
          .validate[List[T]]
          .flatMap(_.toNel.map(t => JsSuccess(t)).getOrElse(JsError(s"Empty list: '$json'.")))
      },
      Writes.list[T].contramap(_.toList)
    )

  implicit val json = Json.format[Errors]
  implicit val html = Writeable.writeableOf_JsValue.map[Errors](es => Json.toJson(es))

  def single(message: String) = Errors(NonEmptyList.of(SingleError(message)))
}
