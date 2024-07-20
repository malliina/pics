package com.malliina.pics

import com.malliina.values.Readable
import org.typelevel.ci.CIStringSyntax

object CSRFConf extends CSRFConf

trait CSRFConf:
  val CsrfTokenName = "csrfToken"
  val CsrfCookieName = "csrfToken"
  val CsrfHeaderName = ci"Csrf-Token"
  val CsrfTokenNoCheck = "nocheck"

opaque type CSRFToken = String

object CSRFToken:
  def apply(s: String): CSRFToken = s

  extension (t: CSRFToken) def value: String = t

  given Readable[CSRFToken] = Readable.string.map(apply)
