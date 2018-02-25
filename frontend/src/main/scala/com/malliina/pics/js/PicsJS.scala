package com.malliina.pics.js

import org.scalajs.dom

object PicsJS {
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
