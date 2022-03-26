package com.malliina.pics.js

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.Dynamic.literal

object PicsJS extends BaseHtml:
  val csrf = CSRFUtils()
  val popperJs = Popper
  val bootstrapJs = Bootstrap
  val bootstrapCss = BootstrapCss
  val fontAwesomeCss = FontAwesomeCss

  def main(args: Array[String]): Unit =
    if has(PicsClass) then
      PicsSocket()
      LazyLoader()
    if has(DropClass) then PicDrop()
    if has(LoginClass) then Login()
    if has(SignUpClass) then SignUp()
    if has(ProfileClass) then Profile()

  def has(feature: String) = dom.document.body.classList.contains(feature)

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
class Popover(e: dom.Element, options: PopoverOptions) extends js.Any:
  def hide(): Unit = js.native
  def show(): Unit = js.native

@js.native
@JSImport("bootstrap/dist/css/bootstrap.min.css", JSImport.Namespace)
object BootstrapCss extends js.Object

@js.native
@JSImport("@fortawesome/fontawesome-free/css/all.min.css", JSImport.Namespace)
object FontAwesomeCss extends js.Object
