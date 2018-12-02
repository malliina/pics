package com.malliina.pics.js

import com.malliina.html.Tags
import com.malliina.pics.HtmlBuilder
import org.scalajs.dom

object PicsJS {

  object jsHtml extends HtmlBuilder(new Tags(scalatags.JsDom))

  val csrf = CSRFUtils()

  def main(args: Array[String]): Unit = {
    if (has(jsHtml.PicsClass)) {
      new PicsSocket
    }
    if (has(jsHtml.DropClass)) {
      new PicDrop
    }
    if (has(jsHtml.LoginClass)) {
      new Login()
    }
    if (has(jsHtml.SignUpClass)) {
      new SignUp()
    }
    if(has(jsHtml.ProfileClass)) {
      new Profile()
    }
  }

  def has(feature: String) = dom.document.body.classList.contains(feature)
}
