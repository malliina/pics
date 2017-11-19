package com.malliina.pics

import java.io.IOException

sealed trait ImageFailure

case class ImageException(ioe: IOException)
  extends ImageFailure

case class UnsupportedFormat(format: String, supported: Seq[String])
  extends ImageFailure
