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

  override protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT] =
    for {
      tokenUse <- parsed.readString(TokenUse)
      _ <- if (tokenUse != Access) Left(InvalidClaims(parsed.token)) else Right(parsed)
    } yield parsed

  protected def toUser(verified: Verified): Either[JWTError, CognitoUser] = {
    val jwt = verified.parsed
    for {
      username <- jwt.readString(UserKey).filterOrElse(_.nonEmpty, InvalidClaims(jwt.token))
      groups <- jwt.readStringList(GroupsKey)
    } yield CognitoUser(Username(username), groups, verified)
  }

}
