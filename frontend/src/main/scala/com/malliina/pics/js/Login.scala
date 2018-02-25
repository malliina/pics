package com.malliina.pics.js

import com.malliina.pics.LoginStrings
import org.scalajs.dom
import org.scalajs.dom.raw.{Event, HTMLElement, HTMLInputElement}

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global, newInstance}
import scala.scalajs.js.annotation.JSExportTopLevel

class Login extends LoginStrings {
  val document = dom.document
  val loginForm = document.getElementById(LoginFormId).asInstanceOf[HTMLElement]
  loginForm.onsubmit = (e: Event) => {
    authenticate()
    e.preventDefault()
  }

  val cognito = global.AWSCognito
  val cognitoIdentity = global.AmazonCognitoIdentity

  cognito.config.region = "eu-west-1"
  val poolData = PoolData("eu-west-1_egi2PEe65", "2rnqepv44epargdosba6nlg2t9")
  val userPool = newInstance(cognitoIdentity.CognitoUserPool)(poolData)

  def authenticate() = {
    val email = document.getElementById(EmailId).asInstanceOf[HTMLInputElement].value
    val pass = document.getElementById(PasswordId).asInstanceOf[HTMLInputElement].value
    val authData = AuthenticationData(email, pass)
    val auth = newInstance(cognitoIdentity.AuthenticationDetails)(authData)
    val userData = UserData(email, userPool)
    val cognitoUser = newInstance(cognitoIdentity.CognitoUser)(userData)
    println(s"Authenticating as '$email'...")
    cognitoUser.authenticateUser(auth, AuthCallback(
      token => println(s"Got token '${token.accessToken.jwtToken}'."),
      fail => println(s"Failed '${fail.message}'."),
      _ => println("MFA required.")
    ))
  }


}

object Auths {
  val cognito = global.AWSCognito
  val cognitoIdentity = global.AmazonCognitoIdentity

  cognito.config.region = "eu-west-1"
  val poolData = PoolData("eu-west-1_egi2PEe65", "2rnqepv44epargdosba6nlg2t9")
  val userPool = newInstance(cognitoIdentity.CognitoUserPool)(poolData)

  @JSExportTopLevel("onGoogleSignIn")
  def onGoogleSignIn(result: GoogleUser): Unit = {
    val idToken = result.authResponse().idToken
    println(s"Signed in, ${result.basicProfile().fullName()} with $idToken")
    val creds = IdentityCredentials.google("eu-west-1:3901927d-32a5-43ff-8ddc-141832b4dbf6", idToken)
    cognito.config.credentials = newInstance(cognito.CognitoIdentityCredentials)(creds)
    cognito.config.credentials.refresh((s: js.Any) => {
      val str = JSON.stringify(s)
      println(s"Joo $str")
//      val str2 = JSON.stringify(cognito.config.credentials)
      println(cognito.config.credentials.Credentials)
      val cognitoUser = userPool.getCurrentUser()
      println(s"User: ${JSON.stringify(cognitoUser)}")
//      if(cognitoUser != null) {
//        println(s"User: ${JSON.stringify(cognitoUser)}")
//      }
      println(cognito.config.credentials.IdentityId)
      val poolData2 = PoolData("eu-west-1_egi2PEe65", "2rnqepv44epargdosba6nlg2t9")
      val userPool2 = newInstance(cognitoIdentity.CognitoUserPool)(poolData2)
      println(userPool.getCurrentUser())
    })
  }
}
