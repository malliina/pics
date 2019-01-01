package com.malliina.pics.js

import org.scalajs.dom
import org.scalajs.jquery.JQueryStatic

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object PicsJS extends BaseHtml {
  val csrf = CSRFUtils()
  private val jq = MyJQuery
  private val p = Popper
  private val b = Bootstrap

  def main(args: Array[String]): Unit = {
    if (has(PicsClass)) {
      new PicsSocket
    }
    if (has(DropClass)) {
      new PicDrop
    }
    if (has(LoginClass)) {
      new Login
    }
    if (has(SignUpClass)) {
      new SignUp
    }
    if (has(ProfileClass)) {
      new Profile
    }
  }

  def has(feature: String) = dom.document.body.classList.contains(feature)
}

@js.native
@JSImport("jquery", JSImport.Namespace)
object MyJQuery extends JQueryStatic

@js.native
@JSImport("popper.js", JSImport.Namespace)
object Popper extends js.Object

@js.native
@JSImport("bootstrap", JSImport.Namespace)
object Bootstrap extends js.Object
