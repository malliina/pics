package com.malliina.pics.js

import org.scalajs.dom

object PicsJS extends BaseHtml {
  val csrf = CSRFUtils()

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
