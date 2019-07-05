package com.malliina.pics.js

import org.scalajs.dom.raw.{Event, HTMLAnchorElement, HTMLFormElement}

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class SignUp(log: BaseLogger = BaseLogger.console) extends AuthFrontend(log) {
  val signupForm = elem[HTMLFormElement](SignUpFormId)
  signupForm.onsubmit = (e: Event) => {
    signUp()
    e.preventDefault()
  }
  val confirm = Confirm(log)

  def signUp(): Unit = {
    val email = emailIn.value
    userPool.signUpEmail(email, passIn.value).map { res =>
      if (res.userConfirmed) {
        // Logs in
        log.info(s"User '$email' already confirmed.")
        login(CognitoUser(email, userPool))
      } else {
        log.info(s"User '$email' is not confirmed, awaiting confirmation code...")
        // Shows confirm
        hide()
        confirm.show()
        confirm.confirmed.map { user =>
          login(user)
        }
      }
    }.feedbackTo(SignUpFeedbackId)
  }

  def hide(): Unit = signupForm.setAttribute("hidden", "hidden")

  def login(user: CognitoUser): Unit =
    user.authenticate(emailIn.value, passIn.value).map { success =>
      submitToken(success.accessToken, LoginTokenId, signupForm)
    }.feedbackTo(SignUpFeedbackId)
}

object Confirm {
  def apply(log: BaseLogger) = new Confirm(log)
}

class Confirm(log: BaseLogger) extends AuthFrontend(log) {
  private val success = Promise[CognitoUser]()
  val confirmed = success.future

  val confirmForm = elem[HTMLFormElement](ConfirmFormId)
  confirmForm.onsubmit = (e: Event) => {
    confirm()
    e.preventDefault()
  }
  elem[HTMLAnchorElement](ResendId).onclick = (_: Event) => {
    resend()
  }

  def show(): Unit = confirmForm.removeAttribute("hidden")

  def confirm(): Unit = {
    val username = emailIn.value
    val user = CognitoUser(username, userPool)
    val code = input(CodeId).value
    user.confirm(code).map { _ =>
      log.info(s"Confirmed user '$username'.")
      success.trySuccess(user)
    }.feedbackTo(ConfirmFeedbackId)
  }

  def resend(): Future[Unit] = {
    val username = emailIn.value
    val user = CognitoUser(username, userPool)
    log.info(s"Resending confirmation code...")
    user.resend().map { _ =>
      log.info(s"Confirmation code resent for '$username'.")
    }.feedbackTo(ConfirmFeedbackId)
  }
}
