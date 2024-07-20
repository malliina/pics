package com.malliina.pics.js

import com.malliina.html.Tags
import com.malliina.pics.{BasicHtmlBuilder, CSRFConf, HtmlBuilder}
import org.scalajs.dom
import scalatags.JsDom.all.{Attr, AttrValue}

class BasicHtml extends BasicHtmlBuilder(new Tags(scalatags.JsDom))

class BaseHtml(csrf: CSRFConf) extends HtmlBuilder(new Tags(scalatags.JsDom), csrf):
  type Builder = dom.Element
  override def makeStringAttr[T](write: T => String): AttrValue[T] =
    (t: Builder, a: Attr, v: T) => t.setAttribute(a.name, write(v))
