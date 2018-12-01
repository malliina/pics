package com.malliina.pics.js

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters.JSRichGenTraversableOnce
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
trait CognitoUserResult extends js.Object {
  def username: String = js.native
}

@js.native
trait SignUpResult extends js.Object {
  def user: CognitoUserResult = js.native

  def userConfirmed: Boolean = js.native

  def userSub: String = js.native
}

@js.native
@JSGlobal("AmazonCognitoIdentity.CognitoUserPool")
class CognitoUserPool(options: PoolData) extends js.Object {
  def signUp(username: String,
             password: String,
             attributes: js.Array[CognitoUserAttribute],
             validationData: js.Any,
             callback: js.Function2[CognitoAuthFailure, SignUpResult, _]): Unit = js.native
}

object CognitoUserPool {
  def apply(options: PoolData) = new CognitoUserPool(options)

  implicit class PoolOps(pool: CognitoUserPool) {
    def signUpEmail(email: String, password: String): Future[SignUpResult] = {
      val attr = CognitoUserAttribute(UserAttribute("email", email))
      val p = Promise[SignUpResult]()
      pool.signUp(email, password, List(attr).toJSArray, null, (err, data) => {
        //        println(s"${JSON.stringify(err)} ${JSON.stringify(data)}")
        Option(err).foreach { e => p.failure(CognitoException(e)) }
        Option(data).foreach { result => p.success(result) }
      })
      p.future
    }
  }

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

  def apply(user: String, pass: String): AuthenticationDetails =
    apply(AuthenticationData(user, pass))
}

@js.native
@JSGlobal("AmazonCognitoIdentity.CognitoUser")
class CognitoUser(options: UserData) extends js.Object {
  def authenticateUser(creds: AuthenticationDetails, callback: AuthCallback): Unit = js.native

  def confirmRegistration(confirmationCode: String,
                          forceAliasCreation: Boolean,
                          callback: js.Function2[CognitoAuthFailure, String, _]): Unit = js.native

  def resendConfirmationCode(callback: js.Function2[CognitoAuthFailure, String, _]): Unit = js.native
}

object CognitoUser {
  def apply(options: UserData): CognitoUser =
    new CognitoUser(options)

  def apply(user: String, pool: CognitoUserPool): CognitoUser =
    apply(UserData(user, pool))

  implicit class CognitoUserOps(val self: CognitoUser) extends AnyVal {
    def authenticate(user: String, pass: String): Future[CognitoAuthSuccess] = {
      val p = Promise[CognitoAuthSuccess]()
      self.authenticateUser(AuthenticationDetails(user, pass), AuthCallback(
        success => p.success(success),
        fail => p.failure(CognitoException(fail)),
        _ => p.tryFailure(new MfaRequiredException)
      ))
      p.future
    }

    def confirm(code: String): Future[Unit] = {
      val p = Promise[Unit]()
      self.confirmRegistration(code, forceAliasCreation = true, (err, success) => {
        Option(err).foreach { err => p.failure(CognitoException(err)) }
        Option(success).foreach { _ => p.success(()) }
      })
      p.future
    }

    def resend(): Future[Unit] = {
      val p = Promise[Unit]()
      self.resendConfirmationCode((err, success) => {
        Option(err).foreach { err =>
          // The API returns an error object on success with message "200"...
          if (err.message == "200") p.trySuccess(())
          else p.failure(CognitoException(err))
        }
        Option(success).foreach { _ => p.success(()) }
      })
      p.future
    }
  }

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
