package com.malliina.pics.html

import cats.Show
import com.malliina.html.HtmlTags
import com.malliina.http.CSRFConf
import com.malliina.pics.{HtmlBuilder, LoginStrings}
import org.http4s.Uri
import scalatags.Text.all.{Attr, AttrValue}
import scalatags.text.Builder

class BaseHtml(csrf: CSRFConf) extends HtmlBuilder(HtmlTags, csrf) with LoginStrings:
  given showAttrValue[T](using s: Show[T]): AttrValue[T] = makeStringAttr(v => s.show(v))
  given AttrValue[Uri] =
    (t: Builder, a: Attr, v: Uri) =>
      t.setAttr(a.name, Builder.GenericAttrValueSource(v.renderString))

  override def makeStringAttr[T](write: T => String): AttrValue[T] =
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(write(v)))
