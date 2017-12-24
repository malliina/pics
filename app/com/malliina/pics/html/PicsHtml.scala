package com.malliina.pics.html

import com.malliina.http.FullUrl
import com.malliina.pics.{HtmlBuilder, PicMeta}
import com.malliina.pics.html.PicsHtml._
import com.malliina.play.controllers.UserFeedback
import com.malliina.play.models.Username
import com.malliina.play.tags.Bootstrap._
import com.malliina.play.tags.PlayTags._
import com.malliina.play.tags.Tags._
import controllers.routes
import play.api.http.MimeTypes
import play.api.mvc.Call

import scala.language.implicitConversions
import scalatags.Text.GenericAttr
import scalatags.Text.all._

object PicsHtml {
  def build(isProd: Boolean): PicsHtml = {
    val jsName = if (isProd) "frontend-opt.js" else "frontend-fastopt.js"
    new PicsHtml(jsName)
  }

  val CopyButton = "copy-button"
  val True = "true"
  val False = "false"
  val dataIdAttr = attr("data-id")
  val dataContentAttr = attr("data-content")
  val defer = attr("defer").empty

  val reverse = routes.PicsController

  implicit def urlWriter(url: FullUrl): scalatags.Text.StringFrag =
    new scalatags.Text.StringFrag(url.url)

  implicit val urlAttr = new GenericAttr[FullUrl]

  def deferredJs(file: String) = deferredJsPath(s"js/$file")

  def deferredJsPath(path: String) = script(`type` := MimeTypes.JAVASCRIPT, src := at(path), defer)

  def at(file: String) = routes.PicsAssets.versioned(file)

  def postableForm(onAction: String, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)
}

class PicsHtml(jsName: String) extends HtmlBuilder(scalatags.Text) {
  def drop(created: Option[PicMeta], feedback: Option[UserFeedback], user: Username) =
    baseIndex("drop", user, deferredJs("drop.js"))(
      divContainer(
        renderFeedback(feedback),
        fullRow(
          postableForm(reverse.sync().toString, `class` := "drop-row form-inline")(
            submitButton(`class` := BtnDefault)("Sync")
          )
        ),
        fullRow(
          postableForm(reverse.delete().toString, `class` := "drop-row form-inline", id := "delete-form")(
            divClass(FormGroup)(
              divClass(InputGroup)(
                divClass(InputGroupAddon)("pics/"),
                input(`type` := Text, `class` := FormControl, name := "key", placeholder := "key")
              )
            ),
            submitButton(`class` := BtnDanger)("Delete")
          )
        ),
        div(`class` := "upload-drop-zone", id := "drop-zone")(
          strong("Drag files here")
        ),
        tag("progress")(id := "progress", min := "0", max := "100", value := "0")(0),
        div(id := "feedback"),
        created.fold(empty) { key =>
          Seq[Modifier]("Saved ", aHref(key.url)(key.key.key))
        }
      )
    )

  def pics(urls: Seq[PicMeta], feedback: Option[UserFeedback], user: Username) =
    baseIndex("pics", user)(
      divClass("pics")(
        renderFeedback(feedback),
        if (urls.isEmpty) {
          leadPara("No pictures.")
        } else {
          divClass("gallery", id := galleryId)(
            urls map { pic =>
              thumbnail(pic)
            }
          )
        }
      )
    )

  def renderFeedback(feedback: Option[UserFeedback]) =
    feedback.fold(empty) { fb =>
      if (fb.isError) alertDanger(fb.message)
      else alertSuccess(fb.message)
    }

  def eject(message: Option[String]) =
    basePage("Goodbye!")(
      divContainer(
        rowColumn(s"$ColMd6 top-padding")(
          message.fold(empty) { msg =>
            div(`class` := s"$Lead $AlertSuccess", role := Alert)(msg)
          }
        )
      )
    )

  def baseIndex(tabName: String, user: Username, extraHeader: Modifier*)(inner: Modifier*) = {
    def navItem(thisTabName: String, tabId: String, url: Call, glyphiconName: String) = {
      val maybeActive = if (tabId == tabName) Option(`class` := "active") else None
      li(maybeActive)(a(href := url)(glyphIcon(glyphiconName), s" $thisTabName"))
    }

    basePage("pics", extraHeader)(
      divClass(s"$Navbar $NavbarDefault")(
        divContainer(
          divClass(NavbarHeader)(
            hamburgerButton,
            a(`class` := NavbarBrand, href := reverse.list())("Pics")
          ),
          divClass(s"$NavbarCollapse $Collapse")(
            ulClass(s"$Nav $NavbarNav")(
              navItem("Pics", "pics", reverse.list(), "picture"),
              navItem("Drop", "drop", reverse.drop(), "upload")
            ),
            ulClass(s"$Nav $NavbarNav $NavbarRight")(
              li(`class` := Dropdown)(
                aHref("#", `class` := DropdownToggle, dataToggle := Dropdown, role := Button, ariaHasPopup := True, ariaExpanded := False)(
                  glyphIcon("user"), s" $user ", spanClass(Caret)
                ),
                ulClass(DropdownMenu)(
                  li(aHref(routes.Admin.logout())(glyphIcon("off"), " Sign Out"))
                )
              )
            )
          )
        )
      ),
      inner
    )
  }

  def basePage(title: String, extraHeader: Modifier*)(inner: Modifier*) = TagPage(
    html(lang := En)(
      head(
        titleTag(title),
        meta(name := "viewport", content := "width=device-width, initial-scale=1.0"),
        cssLink("//netdna.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css"),
        cssLink("//netdna.bootstrapcdn.com/font-awesome/3.2.1/css/font-awesome.css"),
        cssLink("//ajax.googleapis.com/ajax/libs/jqueryui/1.10.4/themes/smoothness/jquery-ui.css"),
        cssLink(at("css/main.css")),
        extraHeader,
        jsScript("//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"),
        jsScript("//ajax.googleapis.com/ajax/libs/jqueryui/1.10.4/jquery-ui.min.js"),
        jsScript("//netdna.bootstrapcdn.com/bootstrap/3.1.1/js/bootstrap.min.js"),
        deferredJsPath(jsName),
      ),
      body(
        section(
          inner
        )
      )
    )
  )
}
