package com.malliina.pics

import java.io.IOException

import play.api.libs.json.Json

sealed trait ImageFailure

case class ImageException(ioe: IOException)
  extends ImageFailure

case class UnsupportedFormat(format: String, supported: Seq[String])
  extends ImageFailure

case class SingleError(message: String, code: String)

object SingleError {
  implicit val json = Json.format[SingleError]

  def apply(message: String): SingleError = apply(message, "generic")
}

case class Errors(errors: Seq[SingleError])

object Errors {
  implicit val json = Json.format[Errors]
}
