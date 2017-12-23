package com.malliina.pics.auth

import java.text.ParseException
import java.time.Instant

import scala.concurrent.duration.{Duration, DurationLong}

sealed abstract class JWTError(val key: String) {
  def token: AccessToken

  def message: String
}

case class Expired(token: AccessToken, exp: Instant, now: Instant)
  extends JWTError("token_expired") {
  def since: Duration = (now.toEpochMilli - exp.toEpochMilli).millis

  override def message = s"Token expired $since ago, at $exp."
}

case class IssuerMismatch(token: AccessToken, actual: String, expected: String)
  extends JWTError("issuer_mismatch") {
  def message = s"Issuer mismatch. Expected '$expected', got '$actual'."
}

case class InvalidSignature(token: AccessToken)
  extends JWTError("invalid_signature") {
  override def message = "Invalid JWT signature."
}

case class InvalidKeyId(token: AccessToken, kid: String, expected: String)
  extends JWTError("invalid_kid") {
  def message = s"Invalid key ID. Expected '$expected', got '$kid'."
}

case class InvalidClaims(token: AccessToken, message: String)
  extends JWTError("invalid_claims")

case class ParseError(token: AccessToken, e: ParseException)
  extends JWTError("parse_error") {
  override def message = "Parse error"
}

case class MissingData(token: AccessToken, message: String)
  extends JWTError("missing_data")
