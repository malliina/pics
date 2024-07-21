package com.malliina.pics

import com.malliina.html.{Bootstrap, Tags, UserFeedback}
import com.malliina.http.{CSRFConf, CSRFToken, FullUrl}

abstract class BasicHtmlBuilder[Builder, Output <: FragT, FragT](ts: Tags[Builder, Output, FragT])
  extends Bootstrap(ts):

  import tags.*
  import tags.impl.all.*

  val DropClass = "drop"
  val FormRole = "form"
  val LoginClass = "login"
  val PicsClass = "pics"
  val Patch = "PATCH"
  val Post = "POST"
  val ProfileClass = "profile"
  val SignUpClass = "signup"
  val Submit = "submit"

  val dataIdAttr = attr("data-id")
  val dataContentAttr = attr("data-content")
  val novalidate = attr("novalidate").empty

  val Button = "button"

  val CopyButton = "copy-button"

  val galleryId = "pic-gallery"
  val picsId = "pics-container"

abstract class HtmlBuilder[Builder, Output <: FragT, FragT](
  ts: Tags[Builder, Output, FragT],
  csrf: CSRFConf
) extends BasicHtmlBuilder(ts):

  import tags.*
  import tags.impl.all.*

  given AttrValue[FullUrl] = makeStringAttr(_.url)
  given AttrValue[Key] = makeStringAttr(_.key)
  given AttrValue[CSRFToken] = makeStringAttr(_.value)

  def makeStringAttr[T](write: T => String): AttrValue[T]

  given Conversion[Key, Frag] = (key) => stringFrag(key.key)

  def noPictures = p(`class` := s"$Lead pics-feedback")("No pictures.")

  def picsContent(urls: Seq[PicMeta], readOnly: Boolean, csrfToken: CSRFToken): Modifier =
    divClass("pics-container", id := picsId)(
      if urls.isEmpty then noPictures
      else gallery(urls, readOnly, lazyLoaded = true, csrfToken = csrfToken)
    )

  def gallery(
    pics: Seq[BaseMeta],
    readOnly: Boolean,
    lazyLoaded: Boolean,
    csrfToken: CSRFToken
  ) =
    divClass("gallery", id := galleryId)(
      pics.map: pic =>
        thumbnail(pic, readOnly, visible = true, lazyLoaded = lazyLoaded, csrfToken = csrfToken)
    )

  def renderFeedback(feedback: Option[UserFeedback]): Modifier =
    feedback.fold(empty): fb =>
      if fb.isError then alertDanger(fb.message)
      else alertSuccess(fb.message)

  def thumbId(key: Key) = s"pic-$key"

  def thumbnail(
    pic: BaseMeta,
    readOnly: Boolean,
    visible: Boolean,
    lazyLoaded: Boolean,
    csrfToken: CSRFToken
  ) =
    val toggleText = if pic.access == Access.Public then "Make private" else "Make public"
    val newAccess = if pic.access == Access.Public then Access.Private else Access.Public
    if readOnly then thumb(pic, visible, lazyLoaded)
    else
      val more = figcaption(`class` := "figure-caption caption")(
        div(
          postableForm(s"/pics/${pic.key}/delete")(
            csrfInput(csrfToken),
            submitButton(`class` := s"${btnOutline.danger} ${btn.sm}")("Delete")
          )
        ),
        div(
          postableForm(s"/pics/${pic.key}")(
            csrfInput(csrfToken),
            input(`type` := "hidden", name := Access.FormKey, value := newAccess.name),
            submitButton(`class` := s"${btnOutline.default} ${btn.sm}")(toggleText)
          )
        ),
        divClass("pic-link")(a(href := pic.url)(pic.key)),
        div(
          a(
            role := Button,
            tabindex := 0,
            `class` := s"${btn.light} ${btn.sm} $CopyButton",
            dataIdAttr := pic.url.toString(),
            data("bs-toggle") := "popover",
            data("bs-content") := "Copied!"
          )("Copy")
        )
      )
      thumb(pic, visible, lazyLoaded, more)

  def csrfInput[V: AttrValue](inputValue: V, inputName: String = csrf.tokenName) =
    input(`type` := "hidden", name := inputName, value := inputValue)

  def thumb(pic: BaseMeta, visible: Boolean, lazyLoaded: Boolean, more: Modifier*) =
    figure(
      `class` := names("figure thumbnail img-thumbnail", if visible then "" else "invisible"),
      id := thumbId(pic.key),
      dataIdAttr := pic.key
    )(
      divClass(names("pic", if more.nonEmpty then "captioned" else ""))(
        a(href := pic.url)(
          img(
            if lazyLoaded then data("src") := pic.small else src := pic.small,
            alt := pic.key,
            `class` := names("thumb", if lazyLoaded then PicsStrings.Lazy else PicsStrings.Loaded)
          )
        )
      ),
      more
    )

  def postableForm(onAction: String, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)

  def names(ns: String*) = ns.filter(_.nonEmpty).mkString(" ")
