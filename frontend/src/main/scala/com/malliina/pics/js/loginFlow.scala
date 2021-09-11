package com.malliina.pics.js

import org.scalajs.dom.raw.*

import scala.concurrent.Promise
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class Login(log: BaseLogger = BaseLogger.console) extends AuthFrontend(log):
  val loginForm = elem[HTMLFormElement](LoginFormId)
  loginForm.onsubmit = (e: Event) =>
    authenticate()
    e.preventDefault()
  val confirm = Confirm(log)
  val forgot = ForgotPassword(log)

  elem[HTMLAnchorElement](ForgotPasswordLinkId).onclick = (_: Event) =>
    hide()
    forgot.show()

  def authenticate(): Unit =
    val email = emailIn.value
    val pass = passIn.value
    val user = CognitoUser(email, userPool)

    user
      .authenticate(email, pass)
      .recoverWith {
        case _: NotConfirmedException =>
          log.info(s"User not confirmed. Awaiting confirmation...")
          hide()
          confirm.show()
          confirm.confirmed.flatMap { user =>
            user.authenticate(email, pass)
          }
        case _: TotpRequiredException =>
          log.info(s"Requesting MFA for '$email'...")
          hide()
          val mfa = Mfa(user, log)
          mfa.show()
          mfa.session
      }
      .map { success =>
        submitToken(success.accessToken, LoginTokenId, loginForm)
      }
      .feedbackTo(LoginFeedbackId)

  def hide(): Unit = loginForm.setAttribute(Hidden, Hidden)

object ForgotPassword:
  def apply(log: BaseLogger): ForgotPassword = new ForgotPassword(log)

class ForgotPassword(log: BaseLogger) extends AuthFrontend(log):
  private val forgotForm = elem[HTMLFormElement](ForgotFormId)
  forgotForm.onsubmit = (e: Event) =>
    sendCode()
    e.preventDefault()
  private val resetForm = elem[HTMLFormElement](ResetFormId)
  resetForm.onsubmit = (e: Event) =>
    reset()
    e.preventDefault()

  private def sendCode(): Unit =
    val email = elem[HTMLInputElement](ForgotEmailId).value
    val user = CognitoUser(email, userPool)
    user
      .forgot()
      .map { _ =>
        log.info(s"Forgot sent to '$email'.")
        hide()
        resetEmailIn.value = email
        elem[HTMLElement](ResetFeedbackId).appendChild(
          alertSuccess(s"Code sent to '$email'.").render
        )
        resetForm.removeAttribute(Hidden)
      }
      .feedbackTo(ForgotFeedbackId)

  private def reset(): Unit =
    val email = resetEmailIn.value
    val user = CognitoUser(email, userPool)
    val code = elem[HTMLInputElement](ResetCodeId).value
    val newPass = elem[HTMLInputElement](ResetNewPasswordId).value
    user
      .reset(code, newPass)
      .flatMap { _ =>
        log.info(s"Password reset for '$email'.")
        user.authenticate(email, newPass)
      }
      .map { success =>
        submitToken(success.accessToken, ResetTokenId, resetForm)
      }
      .feedbackTo(ResetFeedbackId)

  private def resetEmailIn = elem[HTMLInputElement](ResetEmailId)

  def hide(): Unit = forgotForm.setAttribute(Hidden, Hidden)

  def show(): Unit = forgotForm.removeAttribute(Hidden)

object Mfa:
  def apply(user: CognitoUser, log: BaseLogger): Mfa = new Mfa(user, log)

class Mfa(user: CognitoUser, log: BaseLogger) extends AuthFrontend(log):
  private val success = Promise[CognitoSession]()
  val session = success.future

  private val mfaForm = elem[HTMLFormElement](MfaFormId)
  mfaForm.onsubmit = (e: Event) =>
    sendMfa()
    e.preventDefault()

  private def sendMfa() =
    val code = elem[HTMLInputElement](MfaCodeId).value
    user
      .sendMFA(code)
      .map { session =>
        success.trySuccess(session)
      }
      .feedbackTo(MfaFeedbackId)

  def show(): Unit = mfaForm.removeAttribute(Hidden)
