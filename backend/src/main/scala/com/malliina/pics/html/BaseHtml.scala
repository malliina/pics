package com.malliina.pics.html

import cats.Show
import com.malliina.html.HtmlTags
import com.malliina.pics.{HtmlBuilder, LoginStrings}
import org.http4s.Uri
import scalatags.Text.all.{Attr, AttrValue}
import scalatags.text.Builder

class BaseHtml extends HtmlBuilder(HtmlTags) with LoginStrings:
  given showAttrValue[T](using s: Show[T]): AttrValue[T] =
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(s.show(v)))
  given AttrValue[Uri] =
    (t: Builder, a: Attr, v: Uri) =>
      t.setAttr(a.name, Builder.GenericAttrValueSource(v.renderString))
