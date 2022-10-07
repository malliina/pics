package com.malliina.pics.html

import cats.Show
import com.malliina.html.HtmlTags
import com.malliina.pics.{HtmlBuilder, LoginStrings}
import org.http4s.Uri
import scalatags.Text.all.{Attr, AttrValue}
import scalatags.text.Builder

class BaseHtml extends HtmlBuilder(HtmlTags) with LoginStrings:
  implicit def showAttrValue[T](implicit s: Show[T]): AttrValue[T] =
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(s.show(v)))
  implicit val uriAttrValue: AttrValue[Uri] =
    (t: Builder, a: Attr, v: Uri) =>
      t.setAttr(a.name, Builder.GenericAttrValueSource(v.renderString))
