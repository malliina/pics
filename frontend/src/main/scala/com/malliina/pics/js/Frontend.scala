package com.malliina.pics.js

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLInputElement

trait Frontend:
  val document = dom.document

  def input(id: String) = elem[HTMLInputElement](id)

  def elem[T](id: String) = document.getElementById(id).asInstanceOf[T]
