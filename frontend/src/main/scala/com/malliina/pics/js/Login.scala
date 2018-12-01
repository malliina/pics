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
        submitToken(success.accessToken.jwtToken, loginForm)
      }
    }
  }

  def hide(): Unit = loginForm.setAttribute("hidden", "hidden")
}
