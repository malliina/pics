package com.malliina.pics.js

import com.malliina.html.Tags
import com.malliina.pics.{HtmlBuilder, LoginStrings}
import org.scalajs.dom.raw.{HTMLDivElement, HTMLElement, MouseEvent}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.annotation.JSGlobal

/** Looks like Google Authenticator does not accept the secret code, so this does not work.
  *
  * Investigate later.
  */
class Profile(log: BaseLogger = BaseLogger.console) extends AuthFrontend(log) {

  import ProfileHtml._

  val html = PicsJS.jsHtml
  val root = elem[HTMLDivElement](ProfileContainerId)
  val user = userPool.currentUser.map { user =>
    user.session().map { _ =>
      log.info(s"Got session")
      val enable = ProfileHtml.enableButton.render
      root.appendChild(enable)
      enable.onclick = (_: MouseEvent) => {
        user.associateTotp().map { secret =>
          log.info(s"Got secret code $secret")
          val codeContainer = qrCodeContainer.render
          new QRCode(codeContainer, QRCodeOptions(secret, 128, 128))
          root.appendChild(codeContainer)
        }.recover {
          case t =>
            log.error(t)
        }
      }
    }
  }
}

object ProfileHtml extends HtmlBuilder(new Tags(scalatags.JsDom)) {

  import scalatags.JsDom.all._

  val enableButton = button(`class` := btn.primary, "Enable MFA")
  val disableButton = button(`class` := btn.info, "Disable MFA")

  def qrCodeContainer = div(id := LoginStrings.QrCode)
}

@js.native
trait QRCodeOptions extends js.Object {
  def text: String = js.native

  def width: Int = js.native

  def height: Int = js.native
}

object QRCodeOptions {
  def apply(text: String, width: Int, height: Int): QRCodeOptions =
    literal(text = text, width = width, height = height).asInstanceOf[QRCodeOptions]
}

@js.native
@JSGlobal
class QRCode(elem: HTMLElement, options: QRCodeOptions) extends js.Object {
  def clear(): Unit = js.native

  def makeCode(code: String): Unit = js.native
}
