package com.malliina.pics.js

import com.malliina.pics.LoginStrings
import org.scalajs.dom.*

import scala.annotation.unused
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.annotation.JSGlobal

class Profile(log: BaseLogger = BaseLogger.console) extends AuthFrontend(log):

  import ProfileHtml.*

  val root = elem[HTMLDivElement](ProfileContainerId)
  val user = userPool.currentUser.map { user =>
    user.session().map { _ =>
      log.info(s"Got session for ${user.username}")
      val enable = ProfileHtml.enableButton.render
      root.appendChild(enable)
      val disable = disableButton.render
      root.appendChild(disable)
      val signOut = signOutButton.render
      root.appendChild(signOut)
      signOut.onclick = (_: MouseEvent) =>
        user
          .globalLogout()
          .map: _ =>
            log.info("Signed out globally.")
          .recover:
            case t =>
              log.error(t)
      disable.onclick = (_: MouseEvent) =>
        user
          .disableTotp()
          .map: s =>
            log.info(s"Disabled MFA for '${user.username}'. Message was '$s'.")
          .recover:
            case t =>
              log.error(t)
      enable.onclick = (_: MouseEvent) =>
        user
          .associateTotp()
          .map: secret =>
            log.info(s"Got secret code $secret")
            val codeContainer = qrCodeContainer.render
            val str =
              s"otpauth://totp/AWSCognito:${user.username}?secret=$secret&issuer=Amazon%20Cognito"
            new QRCode(codeContainer, QRCodeOptions(str, 128, 128))
            root.appendChild(codeContainer)
            val codeForm = totpForm.render
            root.appendChild(codeForm)
            codeForm.onsubmit = (e: Event) =>
              val code = elem[HTMLInputElement](TotpCodeId).value
              user
                .verifyTotp(code, "TOTP Device")
                .map: _ =>
                  log.info("TOTP verified.")
                  elem[HTMLElement](TotpFeedbackId)
                    .appendChild(alertSuccess(s"MFA enabled.").render)
                .feedbackTo(TotpFeedbackId)
              e.preventDefault()
          .recover:
            case t =>
              log.error(t)
    }
  }

object ProfileHtml extends BasicHtml:

  import scalatags.JsDom.all.*

  val empty: Modifier = ""

  val TotpCodeId = "totp-code"
  val TotpFeedbackId = "totp-feedback"

  val enableButton = button(`class` := btn.primary, "Enable MFA")
  val disableButton = button(`class` := btn.info, "Disable MFA")
  val signOutButton = button(`class` := btn.danger, "Sign Out")

  def qrCodeContainer = div(id := LoginStrings.QrCode)

  def totpForm = form(`class` := col.md.twelve, method := Post, novalidate)(
    div(`class` := FormGroup)(
      labeledInput("Code", TotpCodeId, "number", Option("123456"))
    ),
    divClass(FormGroup)(
      button(`type` := Submit, `class` := btn.primary, "Confirm")
    ),
    divClass(FormGroup, id := TotpFeedbackId)
  )

  def labeledInput(
    labelText: String,
    inId: String,
    inType: String,
    maybePlaceholder: Option[String],
    moreInput: Modifier*
  ) = modifier(
    label(`for` := inId)(labelText),
    input(
      `type` := inType,
      `class` := FormControl,
      id := inId,
      maybePlaceholder.fold(empty)(ph => placeholder := ph),
      moreInput
    )
  )

  def divClass(clazz: String, more: Modifier*) = tags.divClass(clazz, more)

@js.native
trait QRCodeOptions extends js.Object:
  def text: String = js.native

  def width: Int = js.native

  def height: Int = js.native

object QRCodeOptions:
  def apply(text: String, width: Int, height: Int): QRCodeOptions =
    literal(text = text, width = width, height = height).asInstanceOf[QRCodeOptions]

@js.native
@JSGlobal
class QRCode(@unused elem: HTMLElement, @unused options: QRCodeOptions) extends js.Object:
  def clear(): Unit = js.native
  def makeCode(code: String): Unit = js.native
