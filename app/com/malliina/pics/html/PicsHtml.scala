package com.malliina.pics.html

import com.malliina.http.FullUrl
import com.malliina.pics.KeyEntry
import com.malliina.play.models.Username
import com.malliina.play.tags.Bootstrap._
import com.malliina.play.tags.PlayTags._
import com.malliina.play.tags.Tags._
import controllers.{UserFeedback, routes}
import play.api.mvc.Call

import scala.language.implicitConversions
import scalatags.Text.GenericAttr
import scalatags.Text.all._

object PicsHtml {
  val CopyButton = "copy-button"
  val True = "true"
  val False = "false"
  val dataIdAttr = attr("data-id")
  val dataContentAttr = attr("data-content")
  val defer = attr("defer").empty

  implicit def urlWriter(url: FullUrl): scalatags.Text.StringFrag =
    new scalatags.Text.StringFrag(url.url)

  implicit val urlAttr = new GenericAttr[FullUrl]

  def drop(created: Option[KeyEntry], feedback: Option[UserFeedback], user: Username) =
    baseIndex("drop", user, deferredJs("drop.js"))(
      divContainer(
        feedback.fold(empty) { fb =>
          if (fb.isSuccess) alertSuccess(fb.message)
          else alertDanger(fb.message)
        },
        fullRow(
          p(
            postableForm(routes.Home.delete(), `class` := "form-inline", id := "delete-form")(
              divClass(FormGroup)(
                divClass(InputGroup)(
                  divClass(InputGroupAddon)("pics/"),
                  input(`type` := Text, `class` := FormControl, name := "key", placeholder := "key")
                )
              ),
              submitButton(`class` := BtnDanger)("Delete")
            )
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

  def pics(urls: Seq[KeyEntry], feedback: Option[UserFeedback], user: Username) =
    baseIndex("pics", user, deferredJs("pics.js"))(
      divClass("pics")(
        feedback.fold(empty) { fb =>
          if (fb.isSuccess) alertSuccess(fb.message)
          else alertDanger(fb.message)
        },
        if (urls.isEmpty) {
          leadPara("No pictures.")
        } else {
          divClass("gallery")(
            urls map { entry =>
              divClass("thumbnail")(
                divClass("pic")(
                  aHref(entry.url)(
                    img(src := entry.thumb, alt := entry.key.key, `class` := "thumb")
                  )
                ),
                divClass("caption")(
                  div(
                    postableForm(routes.Home.remove(entry.key))(
                      submitButton(`class` := s"$BtnDanger $BtnXs")("Delete")
                    )
                  ),
                  divClass("pic-link")(aHref(entry.url)(entry.key.key)),
                  div(a(role := Button, attr("tabindex") := 0, `class` := s"$BtnDefault $BtnXs $CopyButton", dataIdAttr := entry.url, dataToggle := "popover", dataContentAttr := "Copied!")("Copy"))
                )
              )
            }
          )
        }
      )
    )

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
            a(`class` := NavbarBrand, href := routes.Home.drop())("Pics")
          ),
          divClass(s"$NavbarCollapse $Collapse")(
            ulClass(s"$Nav $NavbarNav")(
              navItem("Drop", "drop", routes.Home.drop(), "upload"),
              navItem("Pics", "pics", routes.Home.list(), "picture")
            ),
            ulClass(s"$Nav $NavbarNav $NavbarRight")(
              li(`class` := Dropdown)(
                aHref("#", `class` := DropdownToggle, dataToggle := Dropdown, role := Button, ariaHasPopup := True, ariaExpanded := False)(
                  glyphIcon("user"), s" $user ", spanClass(Caret)
                ),
                ulClass(DropdownMenu)(
                  li(aHref(routes.Home.logout())(glyphIcon("off"), " Sign Out"))
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
        jsScript("//netdna.bootstrapcdn.com/bootstrap/3.1.1/js/bootstrap.min.js")
      ),
      body(
        section(
          inner
        )
      )
    )
  )

  def deferredJs(file: String) = script(`type` := "text/javascript", src := at(s"js/$file"), defer)

  def at(file: String) = routes.PicsAssets.versioned(file).toString

  def alertDanger(message: Modifier) = alertDiv(AlertDanger)(message)

  def alertSuccess(message: Modifier) = alertDiv(AlertSuccess)(message)

  def alertDiv(alertClass: String, more: Modifier*)(message: Modifier*) =
    divClass(s"$Lead $alertClass", role := Alert, more)(message)

  def postableForm(onAction: Call, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)
}
