package com.malliina.pics.js

import com.malliina.pics.LoginStrings
import org.scalajs.dom.raw.{HTMLElement, HTMLFormElement}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class AuthFrontend(log: BaseLogger) extends Frontend with LoginStrings {
  val poolData = PoolData("eu-west-1_egi2PEe65", "2rnqepv44epargdosba6nlg2t9")
  val userPool = CognitoUserPool(poolData)

  def emailIn = input(EmailId)

  def passIn = input(PasswordId)

  def alertDanger(msg: String) = PicsJS.jsHtml.alertDanger(msg)

  def recovered[T](id: String)(code: Future[T]): Future[T] = code.recoverWith {
    case e: CognitoException =>
      log.error(e)
      elem[HTMLElement](id).appendChild(alertDanger(e.friendlyMessage).render)
      Future.failed(e)
  }

  def submitToken(token: String, to: HTMLFormElement): Unit = {
    input(LoginTokenId).value = token
    PicsJS.csrf.installTo(to)
    to.submit()
  }
}
