package com.malliina.pics.js

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.annotation.JSGlobal

@js.native
trait UserAttribute extends js.Object {
  def Name: String = js.native

  def Value: String = js.native
}

object UserAttribute {
  def apply(name: String, value: String): UserAttribute =
    literal(Name = name, Value = value).asInstanceOf[UserAttribute]
}

@js.native
@JSGlobal("AmazonCognitoIdentity.CognitoUserAttribute")
class CognitoUserAttribute(attribute: UserAttribute) extends js.Object {

}

object CognitoUserAttribute {
  def apply(attribute: UserAttribute): CognitoUserAttribute =
    new CognitoUserAttribute(attribute)
}

@js.native
trait PoolData extends js.Object {
  def UserPoolId: String = js.native

  def ClientId: String = js.native
}

object PoolData {
  def apply(userPoolId: String, clientId: String): PoolData =
    literal(UserPoolId = userPoolId, ClientId = clientId).asInstanceOf[PoolData]
}

@js.native
@JSGlobal("AmazonCognitoIdentity.CognitoUserPool")
class CognitoUserPool(options: PoolData) extends js.Object {

}

object CognitoUserPool {
  def apply(options: PoolData) = new CognitoUserPool(options)
}

@js.native
trait UserData extends js.Object {
  def Username: String = js.native

  def Pool: CognitoUserPool = js.native
}

object UserData {
  def apply(username: String, pool: CognitoUserPool): UserData =
    literal(Username = username, Pool = pool).asInstanceOf[UserData]
}

@js.native
trait AuthenticationData extends js.Object {
  def Username: String = js.native

  def Password: String = js.native
}

object AuthenticationData {
  def apply(username: String, password: String): AuthenticationData =
    literal(Username = username, Password = password).asInstanceOf[AuthenticationData]
}

@js.native
@JSGlobal("AmazonCognitoIdentity.AuthenticationDetails")
class AuthenticationDetails(options: AuthenticationData) extends js.Object {

}

object AuthenticationDetails {
  def apply(options: AuthenticationData): AuthenticationDetails =
    new AuthenticationDetails(options)
}

@js.native
@JSGlobal("AmazonCognitoIdentity.CognitoUser")
class CognitoUser(options: UserData) extends js.Object {
  def authenticateUser(creds: AuthenticationDetails, callback: AuthCallback): Unit = js.native
}

object CognitoUser {
  def apply(options: UserData): CognitoUser =
    new CognitoUser(options)
}

@js.native
trait CognitoAuthSuccess extends js.Object {
  def accessToken: AccessToken = js.native

  def idToken: IdToken = js.native

  def refreshToken: RefreshToken = js.native
}

@js.native
trait CognitoAuthFailure extends js.Object {
  def code: String = js.native

  def name: String = js.native

  def message: String = js.native
}

@js.native
trait AuthCallback extends js.Object {
  def onSuccess: js.Function1[CognitoAuthSuccess, Unit] = js.native

  def onFailure: js.Function1[CognitoAuthFailure, Unit] = js.native

  def mfaRequired: js.Function1[js.Any, Unit] = js.native
}

object AuthCallback {
  def apply(onToken: CognitoAuthSuccess => Unit, onFailure: CognitoAuthFailure => Unit, onMfa: js.Any => Unit): AuthCallback =
    literal(onSuccess = onToken, onFailure = onFailure, mfaRequired = onMfa).asInstanceOf[AuthCallback]
}
