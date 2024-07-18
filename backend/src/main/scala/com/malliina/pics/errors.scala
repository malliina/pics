package com.malliina.pics

import com.malliina.http.Errors
import com.sksamuel.scrimage.ImageParseException

import java.io.IOException
import java.nio.file.Path

sealed trait ImageFailure

case class ImageException(ioe: IOException) extends ImageFailure
case class UnsupportedFormat(format: String, supported: Seq[String]) extends ImageFailure
case class ImageReaderFailure(file: Path) extends ImageFailure
case class ResizeException(ipe: ImageParseException) extends ImageFailure

class KeyNotFound(key: Key) extends Exception(s"Key not found: '$key'.")

class ErrorsException(val errors: Errors) extends Exception(errors.message.message):
  def message = errors.message
