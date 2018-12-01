package com.malliina.pics.js

import com.malliina.pics.LoginStrings
import org.scalajs.dom
import org.scalajs.dom.raw._

class Login extends LoginStrings {
  val document = dom.document
  val loginForm = document.getElementById(LoginFormId).asInstanceOf[HTMLFormElement]
  loginForm.onsubmit = (e: Event) => {
    authenticate()
    e.preventDefault()
  }

  val poolData = PoolData("eu-west-1_egi2PEe65", "2rnqepv44epargdosba6nlg2t9")
  val userPool = CognitoUserPool(poolData)

  def authenticate(): Unit = {
    val email = document.getElementById(EmailId).asInstanceOf[HTMLInputElement].value
    val pass = document.getElementById(PasswordId).asInstanceOf[HTMLInputElement].value
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
      err => document.getElementById("error").asInstanceOf[HTMLInputElement].value = err,
      token => document.getElementById("token").asInstanceOf[HTMLInputElement].value = token
    )
    PicsJS.csrf.installTo(loginForm)
    loginForm.submit()
  }
}
