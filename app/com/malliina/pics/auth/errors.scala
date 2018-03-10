package com.malliina.pics.auth

import java.text.ParseException
import java.time.Instant

import scala.concurrent.duration.{Duration, DurationLong}

sealed abstract class JWTError(val key: String) {
  def token: TokenValue

  def message: String
}

case class Expired(token: TokenValue, exp: Instant, now: Instant)
  extends JWTError("token_expired") {
  def since: Duration = (now.toEpochMilli - exp.toEpochMilli).millis

  override def message = s"Token expired $since ago, at $exp."
}

case class IssuerMismatch(token: TokenValue, actual: String, expected: String)
  extends JWTError("issuer_mismatch") {
  def message = s"Issuer mismatch. Expected '$expected', got '$actual'."
}

case class InvalidSignature(token: TokenValue)
  extends JWTError("invalid_signature") {
  override def message = "Invalid JWT signature."
}

case class InvalidKeyId(token: TokenValue, kid: String, expected: Seq[String])
  extends JWTError("invalid_kid") {
  def message = s"Invalid key ID. Expected one of '${expected.mkString(", ")}', but got '$kid'."
}

case class InvalidClaims(token: TokenValue, message: String)
  extends JWTError("invalid_claims")

case class ParseError(token: TokenValue, e: ParseException)
  extends JWTError("parse_error") {
  override def message = "Parse error"
}

case class MissingData(token: TokenValue, message: String)
  extends JWTError("missing_data")
