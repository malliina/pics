package com.malliina.pics.js

import org.scalajs.dom
import org.scalajs.jquery.JQueryStatic

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object PicsJS extends BaseHtml:
  val csrf = CSRFUtils()
  private val jqueryJs = MyJQuery
  private val popperJs = Popper
  private val bootstrapJs = Bootstrap
  private val bootstrapCss = BootstrapCss
  private val fontAwesomeCss = FontAwesomeCss

  def main(args: Array[String]): Unit =
    if has(PicsClass) then
      new PicsSocket
      LazyLoader()
    if has(DropClass) then new PicDrop
    if has(LoginClass) then new Login
    if has(SignUpClass) then new SignUp
    if has(ProfileClass) then new Profile

  def has(feature: String) = dom.document.body.classList.contains(feature)

@js.native
@JSImport("jquery", JSImport.Namespace)
object MyJQuery extends JQueryStatic

@js.native
@JSImport("popper.js", JSImport.Namespace)
object Popper extends js.Object

@js.native
@JSImport("bootstrap", JSImport.Namespace)
object Bootstrap extends js.Object

@js.native
@JSImport("bootstrap/dist/css/bootstrap.min.css", JSImport.Namespace)
object BootstrapCss extends js.Object

@js.native
@JSImport("@fortawesome/fontawesome-free/css/all.min.css", JSImport.Namespace)
object FontAwesomeCss extends js.Object
