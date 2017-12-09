package com.malliina.pics.auth

import com.malliina.play.models.Username

object CognitoValidator {
  val default = new CognitoValidator(KeyConf.cognito)
}

class CognitoValidator(conf: KeyConf) extends TokenValidator[CognitoUser](conf) {
  val Access = "access"
  val ClientId = "client_id"
  val TokenUse = "token_use"
  val UserKey = "username"
  val GroupsKey = "cognito:groups"

  val ExpectedPicsGroup = "pics-group"
  val ExpectedClientId = "2skjbeoke82rl76jvh2m31arvh"

  def checkClaim(key: String, expected: String, parsed: ParsedJWT): Either[JWTError, ParsedJWT] = {
    parsed.readString(key).flatMap { actual =>
      if (actual == expected) Right(parsed)
      else Left(InvalidClaims(parsed.token, s"Claim '$key' must equal '$expected', was '$actual'."))
    }
  }

  override protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT] =
    for {
      _ <- checkClaim(TokenUse, Access, parsed)
      _ <- checkClaim(ClientId, ExpectedClientId, parsed)
    } yield parsed

  protected def toUser(verified: Verified): Either[JWTError, CognitoUser] = {
    val jwt = verified.parsed
    for {
      username <- jwt.readString(UserKey).filterOrElse(_.nonEmpty, InvalidClaims(jwt.token, "Username must be non-empty."))
      groups <- jwt.readStringListOrEmpty(GroupsKey) // .filterOrElse(_.contains(ExpectedPicsGroup), InvalidClaims(jwt.token, s"User does not belong to group '$PicsGroup'."))
    } yield CognitoUser(Username(username), groups, verified)
  }

}
