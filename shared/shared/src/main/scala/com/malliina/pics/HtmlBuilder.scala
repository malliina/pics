package com.malliina.pics

import com.malliina.html.{Bootstrap, Tags, UserFeedback}
import com.malliina.http.FullUrl

import scala.language.implicitConversions

class HtmlBuilder[Builder, Output <: FragT, FragT](ts: Tags[Builder, Output, FragT])
  extends Bootstrap(ts) {

  import tags._
  import tags.impl.all._

  val FormRole = "form"
  val Post = "POST"
  val Submit = "submit"

  val dataIdAttr = attr("data-id")
  val dataContentAttr = attr("data-content")

  val Button = "button"

  val CopyButton = "copy-button"

  val galleryId = "pic-gallery"
  val picsId = "pics-container"
  val demoButtonId = "demo-button"

  implicit val urlAttr = genericAttr[FullUrl]
  implicit val keyAttr = genericAttr[Key]

  implicit def keyFrag(key: Key): Frag = stringFrag(key.key)

  def noPictures = p(`class` := s"$Lead pics-feedback")("No pictures.")

  def picsContent(urls: Seq[PicMeta], readOnly: Boolean): Modifier =
    divClass("pics-container", id := picsId)(
      if (urls.isEmpty) noPictures
      else gallery(urls, readOnly)
    )

  def gallery(pics: Seq[BaseMeta], readOnly: Boolean) =
    divClass("gallery", id := galleryId)(
      pics map { pic =>
        thumbnail(pic, readOnly)
      }
    )

  def renderFeedback(feedback: Option[UserFeedback]) =
    feedback.fold(empty) { fb =>
      if (fb.isError) alertDanger(fb.message)
      else alertSuccess(fb.message)
    }

  def thumbId(key: Key) = s"pic-$key"

  def thumbnail(pic: BaseMeta, readOnly: Boolean, visible: Boolean = true) = {
    if (readOnly) {
      thumb(pic, visible)
    } else {
      val more = divClass("caption")(
        div(
          postableForm(s"/pics/${pic.key}/delete")(
            submitButton(`class` := s"$BtnDanger $BtnXs")("Delete")
          )
        ),
        divClass("pic-link")(aHref(pic.url)(pic.key)),
        div(a(role := Button,
          tabindex := 0,
          `class` := s"$BtnDefault $BtnXs $CopyButton",
          dataIdAttr := pic.url.toString(),
          dataToggle := "popover",
          dataContentAttr := "Copied!")("Copy"))
      )
      thumb(pic, visible, more)
    }
  }

  def thumb(pic: BaseMeta, visible: Boolean, more: Modifier*) = {
    divClass(names("thumbnail", if (visible) "" else "invisible"), id := thumbId(pic.key), dataIdAttr := pic.key)(
      divClass("pic")(
        aHref(pic.url)(
          img(src := pic.small, alt := pic.key, `class` := "thumb")
        )
      ),
      more
    )
  }

  def postableForm(onAction: String, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)

  def names(ns: String*) = ns.filter(_.nonEmpty).mkString(" ")
}
