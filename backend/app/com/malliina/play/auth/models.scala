package com.malliina.play.auth

import com.malliina.values.{StringCompanion, WrappedString}

case class ClientId(value: String) extends WrappedString
object ClientId extends StringCompanion[ClientId]

case class ClientSecret(value: String) extends WrappedString
object ClientSecret extends StringCompanion[ClientSecret]

case class Issuer(value: String) extends WrappedString
object Issuer extends StringCompanion[Issuer] {
  val apple: Issuer = Issuer("https://appleid.apple.com")
}
