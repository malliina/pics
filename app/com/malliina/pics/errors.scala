package com.malliina.pics

import java.io.IOException
import java.nio.file.Path

import com.malliina.pics.auth.JWTError
import play.api.http.Writeable
import play.api.libs.json.Json

sealed trait ImageFailure

case class ImageException(ioe: IOException)
  extends ImageFailure

case class UnsupportedFormat(format: String, supported: Seq[String])
  extends ImageFailure

case class ImageReaderFailure(file: Path)
  extends ImageFailure

case class SingleError(message: String, key: String)

object SingleError {
  implicit val json = Json.format[SingleError]

  def apply(message: String): SingleError = apply(message, "generic")

  def forJWT(error: JWTError): SingleError =
    SingleError(error.message, error.key)
}

case class Errors(errors: Seq[SingleError])

object Errors {
  implicit val json = Json.format[Errors]
  implicit val html = Writeable.writeableOf_JsValue.map[Errors](es => Json.toJson(es))

  def single(message: String) = Errors(Seq(SingleError(message)))
}
