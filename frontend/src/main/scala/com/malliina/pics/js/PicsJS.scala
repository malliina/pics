package com.malliina.pics.js

import org.scalajs.dom
import org.scalajs.jquery.JQueryStatic

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object PicsJS extends BaseHtml:
  val csrf = CSRFUtils()
  MyJQuery
  Popper
  Bootstrap

  def main(args: Array[String]): Unit =
    println("Init")
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
