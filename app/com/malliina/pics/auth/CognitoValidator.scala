package com.malliina.pics.auth

import com.malliina.play.models.Username

object CognitoValidator {
  val default = new CognitoValidator(KeyConf.cognito)
}

class CognitoValidator(conf: KeyConf) extends TokenValidator(conf) {
  val Access = "access"
  val TokenUse = "token_use"
  val UserKey = "username"
  val GroupsKey = "cognito:groups"

  override protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT] =
    for {
      tokenUse <- parsed.readString(TokenUse)
      _ <- if (tokenUse != Access) Left(InvalidClaims(parsed.token)) else Right(parsed)
    } yield parsed

  protected def readUser(parsed: ParsedJWT): Either[JWTError, CognitoUser] =
    for {
      username <- parsed.readString(UserKey)
      groups <- parsed.readStringList(GroupsKey)
    } yield CognitoUser(Username(username), groups)
}