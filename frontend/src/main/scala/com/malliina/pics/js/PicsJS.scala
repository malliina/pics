package com.malliina.pics.js

import org.scalajs.dom

object PicsJS {
  def main(args: Array[String]): Unit = {
    if (has("pics")) {
      val socket = new PicsSocket
    }
    if (has("drop")) {
      val drop = new PicDrop
    }
  }

  def has(feature: String) = dom.document.body.classList.contains(feature)
}
