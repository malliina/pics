package com.malliina.pics

import scalatags.generic.Bundle

class HtmlBuilder[Builder, Output <: FragT, FragT](val impl: Bundle[Builder, Output, FragT]) {

  import impl.all._

  val FormRole = "form"
  val Post = "POST"
  val Submit = "submit"
  val DataToggle = "data-toggle"

  val dataIdAttr = attr("data-id")
  val dataContentAttr = attr("data-content")

  val Button = "button"

  val Btn = "btn"
  val BtnDanger = "btn btn-danger"
  val BtnDefault = "btn btn-default"
  val BtnGroup = "btn-group"
  val BtnPrimary = "btn btn-primary"
  val BtnLg = "btn-lg"
  val BtnSm = "btn-sm"
  val BtnXs = "btn-xs"
  val BtnBlock = "btn-block"

  val CopyButton = "copy-button"
  val dataToggle = attr(DataToggle)

  val galleryId = "pic-gallery"

  def thumbnail(pic: PicMeta) = {
    divClass("thumbnail")(
      divClass("pic")(
        aHref(pic.url.toString())(
          img(src := pic.small.toString(), alt := pic.key.key, `class` := "thumb")
        )
      ),
      divClass("caption")(
        div(
          postableForm(s"/pics/${pic.key}/delete")(
            submitButton(`class` := s"$BtnDanger $BtnXs")("Delete")
          )
        ),
        divClass("pic-link")(aHref(pic.url.toString())(pic.key.key)),
        div(a(role := Button,
          tabindex := 0,
          `class` := s"$BtnDefault $BtnXs $CopyButton",
          dataIdAttr := pic.url.toString(),
          dataToggle := "popover",
          dataContentAttr := "Copied!")("Copy"))
      )
    )
  }

  def divClass(clazz: String, more: Modifier*) = div(`class` := clazz, more)

  def postableForm(onAction: String, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)

  // WTF? Removing currying requires an AttrValue - should require Modifier?
  def aHref[V: AttrValue](url: V, more: Modifier*)(text: Modifier*) = a(href := url, more)(text)

  def submitButton(more: Modifier*) = button(`type` := Submit, more)

}
