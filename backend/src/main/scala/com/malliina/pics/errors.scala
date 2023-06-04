package com.malliina.pics

import java.io.IOException
import java.nio.file.Path

import cats.data.NonEmptyList
import com.malliina.values.ErrorMessage
import com.sksamuel.scrimage.ImageParseException
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait ImageFailure

case class ImageException(ioe: IOException) extends ImageFailure
case class UnsupportedFormat(format: String, supported: Seq[String]) extends ImageFailure
case class ImageReaderFailure(file: Path) extends ImageFailure
case class ResizeException(ipe: ImageParseException) extends ImageFailure

class KeyNotFound(key: Key) extends Exception(s"Key not found: '$key'.")

case class SingleError(message: ErrorMessage, key: String)

object SingleError:
  implicit val json: Codec[SingleError] = deriveCodec[SingleError]

  def apply(message: String, key: String): SingleError = SingleError(ErrorMessage(message), key)

  def input(message: String) = apply(ErrorMessage(message), "input")

case class Errors(errors: NonEmptyList[SingleError]):
  def message = errors.head.message
  def asException = new ErrorsException(this)

class ErrorsException(val errors: Errors) extends Exception(errors.message.message):
  def message = errors.message

object Errors:
  implicit val json: Codec[Errors] = deriveCodec[Errors]

  def apply(error: SingleError): Errors = Errors(NonEmptyList.of(error))
  def apply(message: String): Errors = apply(message, "generic")
  def apply(e: ErrorMessage): Errors = apply(e.message)
  def apply(message: String, key: String): Errors = apply(SingleError(message, key))
