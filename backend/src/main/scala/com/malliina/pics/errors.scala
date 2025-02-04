package com.malliina.pics

import com.malliina.http.Errors
import com.sksamuel.scrimage.ImageParseException

import java.io.IOException
import java.nio.file.Path

enum ImageFailure:
  case ImageException(ioe: IOException) extends ImageFailure
  case UnsupportedFormat(format: String, supported: Seq[String]) extends ImageFailure
  case ImageReaderFailure(file: Path) extends ImageFailure
  case ResizeException(ipe: ImageParseException) extends ImageFailure

class KeyNotFound(key: Key) extends Exception(s"Key not found: '$key'.")

class ErrorsException(val errors: Errors) extends Exception(errors.message.message):
  def message = errors.message
