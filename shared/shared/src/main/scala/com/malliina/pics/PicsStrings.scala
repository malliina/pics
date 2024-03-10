package com.malliina.pics

import org.typelevel.ci.CIStringSyntax

object PicsStrings extends PicsStrings

trait PicsStrings:
  val Lazy = "lazy"
  val Loaded = "loaded"

  val XClientPic = ci"X-Client-Pic"
  val XKey = ci"X-Key"
  val XName = ci"X-Name"
