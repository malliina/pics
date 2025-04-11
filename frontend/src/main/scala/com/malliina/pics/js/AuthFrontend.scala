package com.malliina.pics.js

import com.malliina.pics.LoginStrings
import org.scalajs.dom.{HTMLElement, HTMLFormElement}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class AuthFrontend(log: BaseLogger) extends Frontend with LoginStrings:
  private val poolData = PoolData.build("eu-west-1_egi2PEe65", "2rnqepv44epargdosba6nlg2t9")
  val userPool = CognitoUserPool(poolData)
  val Hidden = "hidden"

  def emailIn = input(EmailId)
  def passIn = input(PasswordId)

  val html = BasicHtml()

  def alertDanger(msg: String) = html.alertDanger(msg)

  def alertSuccess(msg: String) = html.alertSuccess(msg)

  private def recovered[T](id: String)(code: Future[T]): Future[T] = code.recoverWith:
    case e: CognitoException =>
      log.error(e)
      elem[HTMLElement](id).appendChild(alertDanger(e.friendlyMessage).render)
      Future.failed(e)

  def submitToken(token: AccessToken, inputId: String, to: HTMLFormElement): Unit =
    input(inputId).value = token.jwtToken
    PicsJS.csrf.installTo(to)
    to.submit()

  extension [T](f: Future[T]) def feedbackTo(id: String): Future[T] = recovered(id)(f)
