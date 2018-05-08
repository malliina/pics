package com.malliina.pics.js

import com.malliina.html.Tags
import com.malliina.pics.HtmlBuilder
import org.scalajs.dom

object PicsJS {
  object jsHtml extends HtmlBuilder(new Tags(scalatags.JsDom))
  val csrf = CSRFUtils()

  def main(args: Array[String]): Unit = {
    if (has("pics")) {
      new PicsSocket
    }
    if (has("drop")) {
      new PicDrop
    }
    if (has("login")) {
      new Login
    }
  }

  def has(feature: String) = dom.document.body.classList.contains(feature)
}
