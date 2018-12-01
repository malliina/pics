package com.malliina.pics.js

import com.malliina.pics.LoginStrings
import org.scalajs.dom.raw._

class Login extends Frontend with LoginStrings {
  val loginForm = elem[HTMLFormElement](LoginFormId)
  loginForm.onsubmit = (e: Event) => {
    authenticate()
    e.preventDefault()
  }

  val poolData = PoolData("eu-west-1_egi2PEe65", "2rnqepv44epargdosba6nlg2t9")
  val userPool = CognitoUserPool(poolData)

  def authenticate(): Unit = {
    val email = input(EmailId).value
    val pass = input(PasswordId).value
    val authData = AuthenticationData(email, pass)
    val auth = AuthenticationDetails(authData)
    val userData = UserData(email, userPool)
    val cognitoUser = CognitoUser(userData)
    cognitoUser.authenticateUser(auth, AuthCallback(
      token => submitToken(Right(token.accessToken.jwtToken)),
      fail => {
        println(fail.message)
        submitToken(Left(AuthFailed))
      },
      _ => submitToken(Left(MfaRequired))
    ))
  }

  def submitToken(tokenOrError: Either[String, String]): Unit = {
    tokenOrError.fold(
      err => input(ErrorId).value = err,
      token => input(TokenId).value = token
    )
    PicsJS.csrf.installTo(loginForm)
    loginForm.submit()
  }
}
