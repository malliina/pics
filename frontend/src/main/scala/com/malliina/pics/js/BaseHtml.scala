package com.malliina.pics.js

import com.malliina.html.Tags
import com.malliina.pics.HtmlBuilder
import org.scalajs.dom
import scalatags.JsDom.all.{Attr, AttrValue}

object BaseHtml extends BaseHtml

abstract class BaseHtml extends HtmlBuilder(new Tags(scalatags.JsDom)):
  type Builder = dom.Element
  override def makeStringAttr[T](write: T => String): AttrValue[T] =
    (t: Builder, a: Attr, v: T) => t.setAttribute(a.name, write(v))
