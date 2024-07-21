package com.malliina.pics.js

import com.malliina.html.Tags
import com.malliina.http.CSRFConf
import com.malliina.pics.{BasicHtmlBuilder, HtmlBuilder}
import org.scalajs.dom
import scalatags.JsDom.all.{Attr, AttrValue}

class BasicHtml extends BasicHtmlBuilder(new Tags(scalatags.JsDom))

class BaseHtml(csrf: CSRFConf) extends HtmlBuilder(new Tags(scalatags.JsDom), csrf):
  override def makeStringAttr[T](write: T => String): AttrValue[T] =
    (t: dom.Element, a: Attr, v: T) => t.setAttribute(a.name, write(v))
