package com.malliina.pics.html

import scalatags.Text

case class TagPage(tags: Text.TypedTag[String]) {
  override def toString = tags.toString()
}
