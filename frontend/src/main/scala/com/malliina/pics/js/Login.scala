package com.malliina.pics.js

import com.malliina.pics.LoginStrings
import org.scalajs.dom
import org.scalajs.dom.raw._

import scala.scalajs.js.Dynamic.{global, newInstance}

class Login extends LoginStrings {
  val document = dom.document
  val loginForm = document.getElementById(LoginFormId).asInstanceOf[HTMLFormElement]
  loginForm.onsubmit = (e: Event) => {
    authenticate()
    e.preventDefault()
  }

  val cognito = global.AWSCognito
  val cognitoIdentity = global.AmazonCognitoIdentity

  cognito.config.region = "eu-west-1"
  val poolData = PoolData("eu-west-1_egi2PEe65", "2rnqepv44epargdosba6nlg2t9")
  val userPool = newInstance(cognitoIdentity.CognitoUserPool)(poolData)

  val cognitoUser = userPool.getCurrentUser()

  def authenticate() = {
    val email = document.getElementById(EmailId).asInstanceOf[HTMLInputElement].value
    val pass = document.getElementById(PasswordId).asInstanceOf[HTMLInputElement].value
    val authData = AuthenticationData(email, pass)
    val auth = newInstance(cognitoIdentity.AuthenticationDetails)(authData)
    val userData = UserData(email, userPool)
    val cognitoUser = newInstance(cognitoIdentity.CognitoUser)(userData)
    cognitoUser.authenticateUser(auth, AuthCallback(
      token => submitToken(Right(token.accessToken.jwtToken)),
      fail => {
        println(fail.message)
        submitToken(Left(AuthFailed))
      },
      _ => submitToken(Left(MfaRequired))
    ))

  }

  def submitToken(tokenOrError: Either[String, String]) = {
    tokenOrError.fold(
      err => document.getElementById("error").asInstanceOf[HTMLInputElement].value = err,
      token => document.getElementById("token").asInstanceOf[HTMLInputElement].value = token
    )
    loginForm.submit()
  }
}
