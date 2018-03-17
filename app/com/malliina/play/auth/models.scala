package com.malliina.play.auth

import java.text.ParseException
import java.time.Instant

import com.malliina.http.FullUrl
import com.malliina.json.JsonFormats
import com.malliina.play.models.Email
import com.malliina.values.JsonCompanion
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}

import scala.concurrent.duration.{Duration, DurationLong}

case class AuthConf(clientId: String, clientSecret: String)

object AuthConf {

  def read(key: String) = sys.env.get(key).orElse(sys.props.get(key))
    .toRight(s"Key missing: '$key'. Set it as an environment variable or system property.")

  def orFail(read: Either[String, AuthConf]) = read.fold(err => throw new Exception(err), identity)

  def github = readConf("github_client_id", "github_client_secret")

  def microsoft = readConf("microsoft_client_id", "microsoft_client_secret")

  def google = readConf("google_client_id", "google_client_secret")

  def facebook = readConf("facebook_client_id", "facebook_client_secret")

  def twitter = readConf("twitter_client_id", "twitter_client_secret")

  def readConf(clientIdKey: String, clientSecretKey: String) = {
    val attempt = for {
      clientId <- read(clientIdKey)
      clientSecret <- read(clientSecretKey)
    } yield AuthConf(clientId, clientSecret)
    orFail(attempt)
  }
}

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

case class RequestToken(token: String) extends TokenValue

object RequestToken extends TokenCompanion[RequestToken] {
  val Key = "request_token"
}

abstract class TokenCompanion[T <: TokenValue] extends JsonCompanion[String, T] {
  override def write(t: T) = t.token
}

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class CognitoTokens(accessToken: AccessToken, idToken: IdToken, refreshToken: RefreshToken)

object CognitoTokens {
  implicit val json: Format[CognitoTokens] = (
    (JsPath \ "access_token").format[AccessToken] and
      (JsPath \ "id_token").format[IdToken] and
      (JsPath \ "refresh_token").format[RefreshToken]
    ) (CognitoTokens.apply, unlift(CognitoTokens.unapply))
}

case class GitHubTokens(accessToken: AccessToken, tokenType: String)

object GitHubTokens {
  implicit val json: Format[GitHubTokens] = (
    (JsPath \ "access_token").format[AccessToken] and
      (JsPath \ "token_type").formatWithDefault[String]("dummy")
    ) (GitHubTokens.apply, unlift(GitHubTokens.unapply))
}

case class GitHubEmail(email: Email, primary: Boolean, verified: Boolean, visibility: Option[String])

object GitHubEmail {
  implicit val json = Json.format[GitHubEmail]
}

trait OpenIdConf {
  def jwksUri: FullUrl
}

case class SimpleOpenIdConf(jwksUri: FullUrl) extends OpenIdConf

object SimpleOpenIdConf {
  implicit val reader = Reads[SimpleOpenIdConf] { json =>
    (json \ "jwks_uri").validate[FullUrl].map(apply)
  }
}

case class AuthEndpoints(authorizationEndpoint: FullUrl,
                         tokenEndpoint: FullUrl,
                         jwksUri: FullUrl) extends OpenIdConf

object AuthEndpoints {
  implicit val reader: Reads[AuthEndpoints] = (
    (JsPath \ "authorization_endpoint").read[FullUrl] and
      (JsPath \ "token_endpoint").read[FullUrl] and
      (JsPath \ "jwks_uri").read[FullUrl]
    ) (AuthEndpoints.apply _)
}

case class MicrosoftOAuthConf(authorizationEndpoint: FullUrl,
                              tokenEndpoint: FullUrl,
                              jwksUri: FullUrl,
                              endSessionEndpoint: FullUrl,
                              scopesSupported: Seq[String],
                              issuer: String,
                              claimsSupported: Seq[String]) extends OpenIdConf

object MicrosoftOAuthConf {
  implicit val reader: Reads[MicrosoftOAuthConf] = (
    (JsPath \ "authorization_endpoint").read[FullUrl] and
      (JsPath \ "token_endpoint").read[FullUrl] and
      (JsPath \ "jwks_uri").read[FullUrl] and
      (JsPath \ "end_session_endpoint").read[FullUrl] and
      (JsPath \ "scopes_supported").read[Seq[String]] and
      (JsPath \ "issuer").read[String] and
      (JsPath \ "claims_supported").read[Seq[String]]
    ) (MicrosoftOAuthConf.apply _)
}

trait TokenSet {
  def idToken: IdToken
}

case class SimpleTokens(idToken: IdToken)

object SimpleTokens {
  implicit val reader = Reads[SimpleTokens] { json =>
    (json \ "id_token").validate[IdToken].map(SimpleTokens(_))
  }
}

/** https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-v2-protocols-oauth-code
  *
  * @param idToken      only returned if scope "openid" is requested
  * @param refreshToken only returned if scope "offline_access" is requested
  * @param tokenType    Bearer
  * @param expiresIn    seconds
  */
case class MicrosoftTokens(idToken: IdToken,
                           accessToken: Option[AccessToken],
                           refreshToken: Option[RefreshToken],
                           tokenType: Option[String],
                           expiresIn: Option[Duration],
                           scope: Option[String]) extends TokenSet

object MicrosoftTokens {
  implicit val durFormat = JsonFormats.durationFormat
  implicit val json: Format[MicrosoftTokens] = (
    (JsPath \ "id_token").format[IdToken] and
      (JsPath \ "access_token").formatNullable[AccessToken] and
      (JsPath \ "refresh_token").formatNullable[RefreshToken] and
      (JsPath \ "token_type").formatNullable[String] and
      (JsPath \ "expires_in").formatNullable[Duration] and
      (JsPath \ "scope").formatNullable[String]
    ) (MicrosoftTokens.apply, unlift(MicrosoftTokens.unapply))
}

case class GoogleTokens(idToken: IdToken,
                        accessToken: AccessToken,
                        expiresIn: Duration,
                        tokenType: String) extends TokenSet

object GoogleTokens {
  implicit val durFormat = JsonFormats.durationFormat
  implicit val json: Format[GoogleTokens] = (
    (JsPath \ "id_token").format[IdToken] and
      (JsPath \ "access_token").format[AccessToken] and
      (JsPath \ "expires_in").format[Duration] and
      (JsPath \ "token_type").format[String]
    ) (GoogleTokens.apply, unlift(GoogleTokens.unapply))
}

case class FacebookTokens(accessToken: AccessToken, tokenType: String, expiresIn: Duration)

object FacebookTokens {
  implicit val durFormat = JsonFormats.durationFormat
  implicit val json: Format[FacebookTokens] = (
    (JsPath \ "access_token").format[AccessToken] and
      (JsPath \ "token_type").format[String] and
      (JsPath \ "expires_in").format[Duration]
    ) (FacebookTokens.apply, unlift(FacebookTokens.unapply))
}

case class TwitterTokens(oauthToken: RequestToken, oauthTokenSecret: String, oauthCallbackConfirmed: Boolean)

object TwitterTokens {
  def fromString(in: String) = {
    val map = in.split("&").toList.flatMap { kv =>
      val parts = kv.split("=")
      if (parts.length == 2) {
        val Array(k, v) = parts
        Option(k -> v)
      } else {
        None
      }
    }.toMap
    for {
      ot <- map.get("oauth_token").map(RequestToken.apply)
      ots <- map.get("oauth_token_secret")
      c <- map.get("oauth_callback_confirmed")
    } yield TwitterTokens(ot, ots, c == "true")
  }
}

case class TwitterAccess(oauthToken: AccessToken, oauthTokenSecret: String)

object TwitterAccess {
  def fromString(in: String) = {
    val map = in.split("&").toList.flatMap { kv =>
      val parts = kv.split("=")
      if (parts.length == 2) {
        val Array(k, v) = parts
        Option(k -> v)
      } else {
        None
      }
    }.toMap
    for {
      ot <- map.get("oauth_token").map(AccessToken.apply)
      ots <- map.get("oauth_token_secret")
    } yield TwitterAccess(ot, ots)
  }
}

case class TwitterUser(id: String, name: String, screenName: String, email: Option[Email])

object TwitterUser {
  implicit val json: Format[TwitterUser] = (
    (JsPath \ "id_str").format[String] and
      (JsPath \ "name").format[String] and
      (JsPath \ "screen_name").format[String] and
      (JsPath \ "email").formatNullable[Email]
    ) (TwitterUser.apply, unlift(TwitterUser.unapply))
}

case class EmailResponse(email: Email)

object EmailResponse {
  implicit val json = Json.format[EmailResponse]
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
    StaticTokenValidator.read(token, danger, s"Claim missing: '$key'.")
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
  implicit val reader = Reads[KeyConf] { json =>
    for {
      n <- (json \ "n").validate[String].map(new Base64URL(_))
      kid <- (json \ "kid").validate[String]
      use <- (json \ "use").validate[String].flatMap(parseUse)
      e <- (json \ "e").validate[String].map(new Base64URL(_))
      kty <- (json \ "kty").validate[String]
    } yield KeyConf(n, kid, use, e, JWSAlgorithm.RS256, kty)
  }

  def parseUse(s: String): JsResult[KeyUse] =
    try {
      JsSuccess(KeyUse.parse(s))
    } catch {
      case pe: ParseException => JsError(pe.getMessage)
    }

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

case class JWTKeys(keys: Seq[KeyConf])

object JWTKeys {
  implicit val json = Json.reads[JWTKeys]
}
