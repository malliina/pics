package com.malliina.pics

import com.malliina.html.{Bootstrap, Tags, UserFeedback}
import com.malliina.http.FullUrl

import scala.language.implicitConversions

class HtmlBuilder[Builder, Output <: FragT, FragT](ts: Tags[Builder, Output, FragT])
  extends Bootstrap(ts) {

  import tags._
  import tags.impl.all._

  val DropClass = "drop"
  val FormRole = "form"
  val LoginClass = "login"
  val PicsClass = "pics"
  val Post = "POST"
  val SignUpClass = "signup"
  val Submit = "submit"

  val dataIdAttr = attr("data-id")
  val dataContentAttr = attr("data-content")
  val novalidate = attr("novalidate").empty

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

  def renderFeedback(feedback: Option[UserFeedback]): Modifier =
    feedback.fold(empty) { fb =>
      if (fb.isError) alertDanger(fb.message)
      else alertSuccess(fb.message)
    }

  def thumbId(key: Key) = s"pic-$key"

  def thumbnail(pic: BaseMeta, readOnly: Boolean, visible: Boolean = true) = {
    if (readOnly) {
      thumb(pic, visible)
    } else {
      val more = figcaption(`class` := "figure-caption caption")(
        div(
          postableForm(s"/pics/${pic.key}/delete")(
            submitButton(`class` := s"${btnOutline.danger} ${btn.sm}")("Delete")
          )
        ),
        divClass("pic-link")(a(href := pic.url)(pic.key)),
        div(a(role := Button,
          tabindex := 0,
          `class` := s"${btn.light} ${btn.sm} $CopyButton",
          dataIdAttr := pic.url.toString(),
          dataToggle := "popover",
          dataContentAttr := "Copied!")("Copy"))
      )
      thumb(pic, visible, more)
    }
  }

  def csrfInput(inputName: String, inputValue: String) =
    input(`type` := "hidden", name := inputName, value := inputValue)

  def thumb(pic: BaseMeta, visible: Boolean, more: Modifier*) = {
    figure(
      `class` := names("figure thumbnail img-thumbnail", if (visible) "" else "invisible"),
      id := thumbId(pic.key),
      dataIdAttr := pic.key)(
      divClass(names("pic", if (more.nonEmpty) "captioned" else ""))(
        a(href := pic.url)(
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
