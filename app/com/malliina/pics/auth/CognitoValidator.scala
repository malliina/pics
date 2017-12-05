package com.malliina.pics.auth

import com.malliina.play.models.Username

object CognitoValidator {
  val default = new CognitoValidator(KeyConf.cognito)
}

class CognitoValidator(conf: KeyConf) extends TokenValidator[CognitoUser](conf) {
  val Access = "access"
  val TokenUse = "token_use"
  val UserKey = "username"
  val GroupsKey = "cognito:groups"

  val PicsGroup = "pics-group"

  override protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT] =
    for {
      tokenUse <- parsed.readString(TokenUse)
      _ <- if (tokenUse != Access) Left(InvalidClaims(parsed.token, s"Claim '$TokenUse' must equal '$Access', was '$tokenUse'.")) else Right(parsed)
    } yield parsed

  protected def toUser(verified: Verified): Either[JWTError, CognitoUser] = {
    val jwt = verified.parsed
    for {
      username <- jwt.readString(UserKey).filterOrElse(_.nonEmpty, InvalidClaims(jwt.token, "Username must be non-empty."))
      groups <- jwt.readStringList(GroupsKey).filterOrElse(_.contains(PicsGroup), InvalidClaims(jwt.token, s"User does not belong to group '$PicsGroup'."))
    } yield CognitoUser(Username(username), groups, verified)
  }

}
