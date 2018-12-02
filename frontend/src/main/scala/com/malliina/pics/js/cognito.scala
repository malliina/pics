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

  def getCurrentUser(): CognitoUser = js.native
}

object CognitoUserPool {
  def apply(options: PoolData) = new CognitoUserPool(options)

  implicit class PoolOps(self: CognitoUserPool) {
    def signUpEmail(email: String, password: String): Future[SignUpResult] = {
      val attr = CognitoUserAttribute(UserAttribute("email", email))
      val p = Promise[SignUpResult]()
      self.signUp(email, password, List(attr).toJSArray, null, (err, data) => {
        Option(err).foreach { e => p.failure(CognitoException(e)) }
        Option(data).foreach { result => p.success(result) }
      })
      p.future
    }

    def currentUser: Option[CognitoUser] = Option(self.getCurrentUser())
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
trait MfaSettings extends js.Object {
  def PreferredMfa: Boolean = js.native

  def Enabled: Boolean = js.native
}

object MfaSettings {
  def apply(preferred: Boolean, enabled: Boolean): MfaSettings =
    literal(PreferredMfa = preferred, Enabled = enabled).asInstanceOf[MfaSettings]
}

@js.native
@JSGlobal("AmazonCognitoIdentity.CognitoUser")
class CognitoUser(options: UserData) extends js.Object {
  def username: String = js.native

  def authenticateUser(creds: AuthenticationDetails, callback: AuthCallback): Unit = js.native

  def confirmRegistration(confirmationCode: String,
                          forceAliasCreation: Boolean,
                          callback: js.Function2[CognitoAuthFailure, String, _]): Unit = js.native

  def resendConfirmationCode(callback: js.Function2[CognitoAuthFailure, String, _]): Unit = js.native

  def forgotPassword(callback: SimpleCallback): Unit = js.native

  def confirmPassword(code: String, newPassword: String, callback: SimpleCallback): Unit = js.native

  def setUserMfaPreference(sms: MfaSettings, totp: MfaSettings, callback: js.Function2[js.Error, String, _]): Unit = js.native

  def disableMFA(callback: js.Function2[js.Error, String, _]): Unit = js.native

  def deleteUser(callback: js.Function2[js.Error, String, _]): Unit = js.native

  def getSession(callback: js.Function2[js.Error, CognitoSession, _]): Unit = js.native

  def associateSoftwareToken(callback: TOTPCallback): Unit = js.native

  def verifySoftwareToken(totpCode: String, friendlyDeviceName: String, callback: SessionCallback): Unit = js.native

  def sendMFACode(code: String, callback: SessionCallback, mfaType: String): Unit = js.native

  def globalSignOut(callback: SimpleCallback): Unit = js.native

  def signOut(): Unit = js.native
}

object CognitoUser {
  def apply(options: UserData): CognitoUser =
    new CognitoUser(options)

  def apply(user: String, pool: CognitoUserPool): CognitoUser =
    apply(UserData(user, pool))

  implicit class CognitoUserOps(val self: CognitoUser) extends AnyVal {
    def authenticate(user: String, pass: String): Future[CognitoSession] = {
      val p = Promise[CognitoSession]()
      self.authenticateUser(AuthenticationDetails(user, pass), AuthCallback(
        success => p.success(success),
        fail => p.failure(CognitoException(fail)),
        _ => p.tryFailure(new MfaRequiredException),
        _ => p.tryFailure(new TotpRequiredException),
        (_, _) => ()
      ))
      p.future
    }

    def confirm(code: String): Future[String] = withFuture[String] { p =>
      self.confirmRegistration(code, forceAliasCreation = true, (err, success) => {
        Option(err).foreach { err => p.failure(CognitoException(err)) }
        Option(success).foreach { s => p.success(s) }
      })
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

    def forgot(): Future[Unit] =
      promised(cb => self.forgotPassword(cb))

    def reset(code: String, newPassword: String): Future[Unit] =
      promised(cb => self.confirmPassword(code, newPassword, cb))

    def session(): Future[CognitoSession] = withFuture[CognitoSession] { p =>
      self.getSession((err, success) => complete(p, err, success))
    }

    def enableTotp(): Future[String] = withFuture[String] { p =>
      val settings = MfaSettings(preferred = true, enabled = true)
      self.setUserMfaPreference(null, settings, (err, success) => {
        complete(p, err, success)
      })
    }

    def disableTotp(): Future[String] = withFuture[String] { p =>
      self.disableMFA((err, success) => complete(p, err, success))
    }

    def delete(): Future[String] = withFuture[String] { p =>
      self.deleteUser((err, success) => complete(p, err, success))
    }

    def associateTotp(): Future[String] = withFuture[String] { p =>
      self.associateSoftwareToken(TOTPCallback(
        s => p.success(s),
        f => p.failure(CognitoException(f))
      ))
    }

    def verifyTotp(code: String, friendlyName: String): Future[CognitoSession] =
      withFuture[CognitoSession] { p =>
        self.verifySoftwareToken(code, friendlyName, SessionCallback(
          success => p.success(success),
          fail => p.failure(CognitoException(fail))
        ))
      }

    def sendMFA(code: String): Future[CognitoSession] =
      withFuture[CognitoSession] { p =>
        self.sendMFACode(code, SessionCallback(
          success => p.success(success),
          fail => p.failure(CognitoException(fail))
        ), "SOFTWARE_TOKEN_MFA")
      }

    def globalLogout(): Future[Unit] = promised(cb => self.globalSignOut(cb))

    private def withFuture[T](run: Promise[T] => Unit): Future[T] = {
      val p = Promise[T]()
      run(p)
      p.future
    }

    private def complete[T](p: Promise[T], err: js.Error, t: T): Unit = {
      Option(err).foreach { err => p.failure(CognitoException(err)) }
      Option(t).foreach { t => p.success(t) }
    }

    private def promised(run: SimpleCallback => Unit): Future[Unit] = {
      val p = Promise[Unit]()
      val callback = SimpleCallback(_ => p.success(()), fail => p.failure(CognitoException(fail)))
      run(callback)
      p.future
    }
  }

}

@js.native
trait CognitoSession extends js.Object {
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
trait SimpleCallback extends js.Object {
  def onSuccess: js.Function1[js.Any, Unit] = js.native

  def onFailure: js.Function1[CognitoAuthFailure, Unit] = js.native
}

object SimpleCallback {
  def apply(onSuccess: js.Any => Unit, onFailure: CognitoAuthFailure => Unit): SimpleCallback =
    literal(onSuccess = onSuccess, onFailure = onFailure).asInstanceOf[SimpleCallback]
}

@js.native
trait AuthCallback extends js.Object {
  def onSuccess: js.Function1[CognitoSession, Unit] = js.native

  def onFailure: js.Function1[CognitoAuthFailure, Unit] = js.native

  def mfaRequired: js.Function1[js.Any, Unit] = js.native

  def totpRequired: js.Function1[js.Any, Unit] = js.native

  def selectMFAType: js.Function2[String, js.Any, Unit] = js.native

  def mfaSetup: js.Function2[String, js.Any, Unit] = js.native
}

object AuthCallback {
  def apply(onToken: CognitoSession => Unit,
            onFailure: CognitoAuthFailure => Unit,
            onMfa: js.Any => Unit,
            onTotp: js.Any => Unit,
            mfaSetup: (String, js.Any) => Unit): AuthCallback =
    literal(
      onSuccess = onToken,
      onFailure = onFailure,
      mfaRequired = onMfa,
      totpRequired = onTotp,
      mfaSetup = mfaSetup).asInstanceOf[AuthCallback]
}

@js.native
trait SessionCallback extends js.Object {
  def onSuccess: js.Function1[CognitoSession, Unit] = js.native

  def onFailure: js.Function1[CognitoAuthFailure, Unit] = js.native
}

object SessionCallback {
  def apply(onToken: CognitoSession => Unit,
            onFailure: CognitoAuthFailure => Unit): SessionCallback =
    literal(
      onSuccess = onToken,
      onFailure = onFailure).asInstanceOf[SessionCallback]
}

@js.native
trait TOTPCallback extends js.Object {
  def onFailure: js.Function1[CognitoAuthFailure, Unit] = js.native

  def associateSecretCode: js.Function1[String, Unit] = js.native
}

object TOTPCallback {
  def apply(onCode: String => Unit, onFailure: CognitoAuthFailure => Unit): TOTPCallback =
    literal(associateSecretCode = onCode, onFailure = onFailure).asInstanceOf[TOTPCallback]
}
