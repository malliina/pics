package com.malliina.pics.auth

import com.malliina.pics.PicOwner
import com.malliina.play.models.Email
import play.api.Logger

object CognitoValidator {
  private val log = Logger(getClass)

  val Access = "access"
  val Aud = "aud"
  val Id = "id"
  val ClientId = "client_id"
  val TokenUse = "token_use"
  val UserKey = "username"
  val EmailKey = "email"
  val GroupsKey = "cognito:groups"

  val ExpectedPicsGroup = "pics-group"

  val picsAccess = new CognitoAccessValidator(
    Seq(KeyConf.cognitoAccess, KeyConf.cognitoId),
    "https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65",
    "2rnqepv44epargdosba6nlg2t9"
  )

  val picsId = new CognitoIdValidator(
    Seq(KeyConf.cognitoAccess, KeyConf.cognitoId),
    "https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_egi2PEe65",
    "2rnqepv44epargdosba6nlg2t9"
  )
}

case class CognitoValidation(issuer: String,
                             tokenUse: String,
                             clientIdKey: String,
                             clientId: String)

import com.malliina.pics.auth.CognitoValidator._

abstract class CognitoValidator[T <: TokenValue, U](keys: Seq[KeyConf], issuer: String)
  extends TokenValidator[T, U](keys, issuer) {

  def checkClaim(key: String, expected: String, parsed: ParsedJWT): Either[JWTError, ParsedJWT] = {
    parsed.readString(key).flatMap { actual =>
      if (actual == expected) Right(parsed)
      else Left(InvalidClaims(parsed.token, s"Claim '$key' must equal '$expected', was '$actual'."))
    }
  }

  def checkContains(key: String, expected: String, parsed: ParsedJWT): Either[JWTError, Seq[String]] = {
    parsed.readStringListOrEmpty(key).flatMap { arr =>
      if (arr.contains(expected)) Right(arr)
      else Left(InvalidClaims(parsed.token, s"Claim '$key' does not contain '$expected', was '${arr.mkString(", ")}'."))
    }
  }
}

class CognitoAccessValidator(keys: Seq[KeyConf], issuer: String, clientId: String)
  extends CognitoValidator[AccessToken, CognitoUser](keys, issuer) {

  protected def toUser(verified: Verified): Either[JWTError, CognitoUser] = {
    val jwt = verified.parsed
    for {
      username <- jwt.readString(UserKey).filterOrElse(_.nonEmpty, InvalidClaims(jwt.token, "Username must be non-empty."))
      email <- jwt.readStringOpt(EmailKey)
      groups <- jwt.readStringListOrEmpty(GroupsKey) // .filterOrElse(_.contains(ExpectedPicsGroup), InvalidClaims(jwt.token, s"User does not belong to group '$PicsGroup'."))
    } yield CognitoUser(PicOwner(username), email.map(Email.apply), groups, verified)
  }

  override protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT] =
    for {
      _ <- checkClaim(TokenUse, Access, parsed)
      _ <- checkClaim(ClientId, clientId, parsed)
    } yield parsed
}

class CognitoIdValidator(keys: Seq[KeyConf], issuer: String, clientId: String)
  extends CognitoValidator[IdToken, CognitoUser](keys, issuer) {

  override protected def toUser(verified: Verified): Either[JWTError, CognitoUser] = {
    val jwt = verified.parsed
    for {
      email <- jwt.readString(EmailKey).map(Email.apply)
      groups <- jwt.readStringListOrEmpty(GroupsKey)
    } yield CognitoUser(PicOwner(email.email), Option(email), groups, verified)
  }

  override protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT] =
    for {
      _ <- checkClaim(TokenUse, Id, parsed)
      _ <- checkContains(Aud, clientId, parsed)
    } yield parsed
}
