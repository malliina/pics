package com.malliina.pics.auth

import java.time.Instant

import com.malliina.values.JsonCompanion
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}

import scala.concurrent.duration.{Duration, DurationLong}

sealed trait TokenValue {
  def token: String

  override def toString = token
}

case class AccessToken(token: String) extends TokenValue

object AccessToken extends TokenCompanion[AccessToken]

case class IdToken(token: String) extends TokenValue

object IdToken extends TokenCompanion[IdToken]

case class RefreshToken(token: String) extends TokenValue

object RefreshToken extends TokenCompanion[RefreshToken]

abstract class TokenCompanion[T <: TokenValue] extends JsonCompanion[String, T] {
  override def write(t: T) = t.token
}

case class CognitoTokens(accessToken: AccessToken, idToken: IdToken, refreshToken: RefreshToken)

object CognitoTokens {

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit val json: Format[CognitoTokens] = (
    (JsPath \ "access_token").format[AccessToken] and
      (JsPath \ "id_token").format[IdToken] and
      (JsPath \ "refresh_token").format[RefreshToken]
    ) (CognitoTokens.apply, unlift(CognitoTokens.unapply))
}

case class ParsedJWT(jwt: SignedJWT,
                     claims: JWTClaimsSet,
                     kid: String,
                     iss: String,
                     exp: Instant,
                     token: TokenValue) {

  import scala.collection.JavaConverters.asScalaBufferConverter

  def readString(key: String): Either[JWTError, String] =
    read(claims.getStringClaim(key), key)

  def readStringOpt(key: String) = read(Option(claims.getStringClaim(key)), key)

  def readStringListOrEmpty(key: String): Either[JWTError, Seq[String]] =
    readStringList(key).map(_.getOrElse(Nil))

  def readStringList(key: String): Either[JWTError, Option[Seq[String]]] =
    read(Option(claims.getStringListClaim(key)).map(_.asScala), key)

  def read[T](danger: => T, key: String): Either[JWTError, T] =
    TokenValidator.read(token, danger, s"Claim missing: '$key'.")
}

case class Verified(parsed: ParsedJWT) {
  def expiresIn: Duration = (parsed.exp.toEpochMilli - Instant.now().toEpochMilli).millis
}

case class KeyConf(n: Base64URL,
                   kid: String,
                   use: KeyUse,
                   e: Base64URL,
                   alg: JWSAlgorithm,
                   kty: String)

object KeyConf {
  val cognito = rsa(
    "poGdLTTbCHKgSg8uXc8jCNmzBg6CBbREelhbdwNwdt0HpbEKnt5D3IeateCPVv2zAwXHaRmT0OzgOz2T9nZnD1nLRO_cisIwfFQfrlooKO3xklLUleTnkBx2mLqgzvpvZ6y9oqgduH2ekGt7Pz8z9sB3T7X-4QBeuDaV67mxhoGtRnldDrOpHAXf4ILeuDXL-8-R7WfDHehlDofU4OU6Xhpe5gT0oj-L9q5T63IfTpblS5aKx346YfVjN1dx3G1Urclf6cTPSQpqgYYH1gx98Mf5T2UcRQ_GZSO7St1MBz9psfdlvP0kiegM4_mqyM_GzI5mLEss3KktdMhzBZZUJQ",
    "Ord14hhhzSdst7wfmIK59oBMEdxIEEerDlP3M5sjYCY=",
    "https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65"
  )

  val cognitoAccess = rsa(
    "poGdLTTbCHKgSg8uXc8jCNmzBg6CBbREelhbdwNwdt0HpbEKnt5D3IeateCPVv2zAwXHaRmT0OzgOz2T9nZnD1nLRO_cisIwfFQfrlooKO3xklLUleTnkBx2mLqgzvpvZ6y9oqgduH2ekGt7Pz8z9sB3T7X-4QBeuDaV67mxhoGtRnldDrOpHAXf4ILeuDXL-8-R7WfDHehlDofU4OU6Xhpe5gT0oj-L9q5T63IfTpblS5aKx346YfVjN1dx3G1Urclf6cTPSQpqgYYH1gx98Mf5T2UcRQ_GZSO7St1MBz9psfdlvP0kiegM4_mqyM_GzI5mLEss3KktdMhzBZZUJQ",
    "Ord14hhhzSdst7wfmIK59oBMEdxIEEerDlP3M5sjYCY=",
    "https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65"
  )

  val cognitoId = rsa(
    "mJXj-YNaLTz69y8Yb1kxqYTWJlYET9CLIygMQnHf8tM4skt-2vehKhlGFveJvIsL9zZcnHWn18KLKuguc5S35vlm-hVW8xbJBQxRfKqNwqCLjkN-6AKNdqL2aAFP-5xSoxMbmdO2rtpgdZTuaBg--ilUDD8zCStxOYXmySOM09lFYMJetl3T2Pg2DXWbmJnSPTTkN6mTlaLqDM6Lt6p7mgcJab7wrUBNZn7wTYuxqOp_yJRbtvp_c-A1pn5Z05NfB9O5F-yd3kV18Ycd3A4Ed7iISTJ7fwIgHtTivPyYfq-xz0U-cHw6OZ-iJ-K0kzeCPhrOjBS_i5BOkLVurJct-w",
    "qTq8YGrI/Ntl8eev826XB2mDKoXfAeS5nFHRc78QMxg=",
    "https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65"
  )

  val auth0 = rsa(
    "mj12OjmpCouG9QUQrbq3lWYyvOEgdsN3zyQFdiwpkbtM1X0-u3eUq5GGEeRExbWiEmmsNELa4WjwWHblBgidB10lqBxdU-zH24lji_F6hqNHc3IrWj8rqO4Lf1JPOs02DQdEVe-YxGOi_iJtk0JWx8RSXNXavy7V6ADrR8zoLgQjyV0ExorqSCb-yCx3j4A6KIf_sF9yUedGXTdwfaIF5FRUr6odwlMMmJHqvQI44PUYUH8CsxezJHt6sForht4OzOi6X7xoL8mBTo-Rp8jSEuZKqEbKtBuDebkCCBXRREfckEVRCfeuiTAYlgGTihQGg6Ko6EIg3Di3IlwZWw1Emw",
    "OTc5Rjc5MUU3M0M5MjM2OUJFNUM1MjJBRjczREUwQTYyMkRFMTdBRA",
    "https://malliina.eu.auth0.com/"
  )

  def rsa(n: String, kid: String, issuer: String) = KeyConf(
    new Base64URL(n),
    kid,
    KeyUse.SIGNATURE,
    new Base64URL("AQAB"),
    JWSAlgorithm.RS256,
    "RSA"
  )
}
