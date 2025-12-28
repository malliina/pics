package com.malliina.pics.js

import com.malliina.http.CSRFConf
import org.scalajs.dom

import scala.annotation.unused
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.Dynamic.literal

object PicsJS extends BasicHtml:
  val log = LogSocket.instance

  val csrfConf = CSRFConf.default
  val csrf = CSRFUtils(csrfConf, LogSocket.instance)
  val popperJs = Popper
  val bootstrapJs = Bootstrap
  val bootstrapCss = BootstrapCss
  val commonCss = CommonCss
  val galleryCss = GalleryCss
  val footerCss = FooterCss
  val dropCss = DropCss
  val authCss = AuthCss
  val profileCss = ProfileCss
  val navCss = NavCss
  val fontsCss = FontsCss

  def main(args: Array[String]): Unit =
    if has(PicsClass) then
      PicsSocket(csrf, csrfConf, log)
      LazyLoader()
    if has(DropClass) then PicDrop(csrfConf)
    if has(LoginClass) then Login(log)
    if has(SignUpClass) then SignUp(log)
    if has(ProfileClass) then Profile(log)

  private def has(feature: String) = dom.document.body.classList.contains(feature)

@js.native
@JSImport("@popperjs/core", JSImport.Namespace)
object Popper extends js.Object

@js.native
trait PopoverOptions extends js.Object:
  def trigger: String

object PopoverOptions:
  def apply(trigger: String) = literal(trigger = trigger).asInstanceOf[PopoverOptions]
  val click = apply("click")
  val focus = apply("focus")
  val manual = apply("manual")

@js.native
@JSImport("bootstrap", JSImport.Namespace)
object Bootstrap extends js.Object

@js.native
@JSImport("bootstrap", "Popover")
class Popover(@unused e: dom.Element, @unused options: PopoverOptions) extends js.Any:
  def hide(): Unit = js.native
  def show(): Unit = js.native

@js.native
@JSImport("bootstrap/dist/css/bootstrap.min.css", JSImport.Namespace)
object BootstrapCss extends js.Object

@js.native
@JSImport("./css/common.css", JSImport.Namespace)
object CommonCss extends js.Object

@js.native
@JSImport("./css/gallery.css", JSImport.Namespace)
object GalleryCss extends js.Object

@js.native
@JSImport("./css/footer.css", JSImport.Namespace)
object FooterCss extends js.Object

@js.native
@JSImport("./css/drop.css", JSImport.Namespace)
object DropCss extends js.Object

@js.native
@JSImport("./css/auth.css", JSImport.Namespace)
object AuthCss extends js.Object

@js.native
@JSImport("./css/profile.css", JSImport.Namespace)
object ProfileCss extends js.Object

@js.native
@JSImport("./css/navigation.css", JSImport.Namespace)
object NavCss extends js.Object

@js.native
@JSImport("./css/fonts.css", JSImport.Namespace)
object FontsCss extends js.Object
