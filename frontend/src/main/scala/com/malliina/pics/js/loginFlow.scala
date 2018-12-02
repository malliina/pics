package com.malliina.pics.js

import org.scalajs.dom.raw._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class Login(log: BaseLogger = BaseLogger.console) extends AuthFrontend(log) {
  val loginForm = elem[HTMLFormElement](LoginFormId)
  loginForm.onsubmit = (e: Event) => {
    authenticate()
    e.preventDefault()
  }
  val confirm = Confirm(log)
  val forgot = ForgotPassword(log)

  elem[HTMLAnchorElement](ForgotPasswordLinkId).onclick = (_: Event) => {
    hide()
    forgot.show()
  }

  def authenticate(): Unit = {
    val email = emailIn.value
    val pass = passIn.value
    val user = CognitoUser(email, userPool)

    recovered(LoginFeedbackId) {
      user.authenticate(email, pass).recoverWith {
        case _: NotConfirmedException =>
          log.info(s"User not confirmed. Awaiting confirmation...")
          hide()
          confirm.show()
          confirm.confirmed.flatMap { user =>
            user.authenticate(email, pass)
          }
      }.map { success =>
        submitToken(success.accessToken.jwtToken, LoginTokenId, loginForm)
      }
    }
  }

  def hide(): Unit = loginForm.setAttribute(Hidden, Hidden)
}

object ForgotPassword {
  def apply(log: BaseLogger): ForgotPassword = new ForgotPassword(log)
}

class ForgotPassword(log: BaseLogger) extends AuthFrontend(log) {
  val forgotForm = elem[HTMLFormElement](ForgotFormId)
  forgotForm.onsubmit = (e: Event) => {
    sendCode()
    e.preventDefault()
  }
  val resetForm = elem[HTMLFormElement](ResetFormId)
  resetForm.onsubmit = (e: Event) => {
    reset()
    e.preventDefault()
  }

  def sendCode(): Unit = {
    val email = elem[HTMLInputElement](ForgotEmailId).value
    val user = CognitoUser(email, userPool)
    recovered(ForgotFeedbackId) {
      user.forgot().map { _ =>
        log.info(s"Forgot sent to '$email'.")
        hide()
        resetEmailIn.value = email
        elem[HTMLElement](ResetFeedbackId).appendChild(alertSuccess(s"Code sent to '$email'.").render)
        resetForm.removeAttribute(Hidden)
      }
    }
  }

  def reset(): Unit = {
    val email = resetEmailIn.value
    val user = CognitoUser(email, userPool)
    val code = elem[HTMLInputElement](ResetCodeId).value
    val newPass = elem[HTMLInputElement](ResetNewPasswordId).value
    recovered(ResetFeedbackId) {
      user.reset(code, newPass).flatMap { _ =>
        log.info(s"Password reset for '$email'.")
        user.authenticate(email, newPass)
      }.map { success =>
        submitToken(success.accessToken.jwtToken, ResetTokenId, resetForm)
      }
    }
  }

  def resetEmailIn = elem[HTMLInputElement](ResetEmailId)

  def hide(): Unit = forgotForm.setAttribute(Hidden, Hidden)

  def show(): Unit = forgotForm.removeAttribute(Hidden)
}
