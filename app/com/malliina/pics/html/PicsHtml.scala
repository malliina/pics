package com.malliina.pics.html

import com.malliina.html.UserFeedback
import com.malliina.http.FullUrl
import com.malliina.pics.html.PicsHtml._
import com.malliina.pics.{HtmlBuilder, PicMeta, PicOwner, PicRequest}
import com.malliina.play.models.Username
import com.malliina.play.tags.PlayTags._
import com.malliina.play.tags.Tags._
import controllers.routes
import play.api.http.MimeTypes
import play.api.mvc.Call

import scala.language.implicitConversions
import scalatags.Text
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

class PicsHtml(jsName: String) extends HtmlBuilder(new com.malliina.html.Tags(scalatags.Text)) {
  implicit def userFrag(user: PicOwner): Text.StringFrag = stringFrag(user.name)

  def drop(created: Option[PicMeta], feedback: Option[UserFeedback], user: PicRequest) = {
    val content =
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
                input(`type` := "text", `class` := FormControl, name := "key", placeholder := "key")
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
    val conf = PageConf("Pics - Drop", bodyClass = "drop", inner = content)
    baseIndex("drop", user, conf)
  }

  def pics(urls: Seq[PicMeta], feedback: Option[UserFeedback], user: PicRequest) = {
    val content = Seq(divClass("pics")(renderFeedback(feedback)), picsContent(urls, user.readOnly))
    val conf = PageConf("Pics", bodyClass = "pics", inner = content)
    baseIndex("pics", user, conf)
  }

  def privacyPolicy = {
    val privacyContent: Modifier = divContainer(
      h1("Privacy Policy"),
      p("This privacy policy describes how your information is used and stored when you use this app."),
      p("The purpose of using and storing your information is to enable app functionality and optimize your user experience. Your information is not used for any other purposes than enabling application features. Your information is not shared with third parties."),
      p("Network communications: This app may communicate with other networked servers. The communication enables the transfer of images to and from your devices."),
      p("Network requests may be logged by server software.")
    )
    basePage(PageConf("Pics - Privacy Policy", inner = privacyContent))
  }

  def support = {
    val content = divContainer(
      h1("Support"),
      p("For support issues:"),
      ul(
        li("Email us at ", aHref("mailto:info@skogberglabs.com")("info@skogberglabs.com")),
        li("Open an issue on ", aHref("https://github.com/malliina/pics-ios/issues")("GitHub"))
      )
    )
    basePage(PageConf("Pics - Support", inner = content))
  }

  def eject(message: Option[String]) = {
    val content =
      divContainer(
        rowColumn(s"$ColMd6 top-padding")(
          message.fold(empty) { msg =>
            div(`class` := s"$Lead $AlertSuccess", role := Alert)(msg)
          }
        )
      )
    basePage(PageConf("Goodbye!", inner = content))
  }

  def baseIndex(tabName: String, user: PicRequest, conf: PageConf) = {
    def navItem(thisTabName: String, tabId: String, url: Call, glyphiconName: String) = {
      val maybeActive = if (tabId == tabName) Option(`class` := "active") else None
      li(maybeActive)(a(href := url)(glyphIcon(glyphiconName), s" $thisTabName"))
    }

    val navContent =
      if (user.readOnly) {
        ulClass(s"$Nav $NavbarNav $NavbarRight")(
          navItem("Sign In", "signin", reverse.signIn(), "log-in")
        )
      } else {
        modifier(
          ulClass(s"$Nav $NavbarNav")(
            navItem("Pics", "pics", reverse.list(), "picture"),
            navItem("Drop", "drop", reverse.drop(), "upload")
          ),
          ulClass(s"$Nav $NavbarNav $NavbarRight")(
            li(`class` := Dropdown)(
              aHref("#", `class` := DropdownToggle, dataToggle := Dropdown, role := Button, ariaHasPopup := True, ariaExpanded := False)(
                glyphIcon("user"), " ", user.name, " ", spanClass(Caret)
              ),
              ulClass(DropdownMenu)(
                li(aHref(routes.Admin.logout())(glyphIcon("off"), " Sign Out"))
              )
            )
          )
        )
      }
    basePage(conf.copy(inner = modifier(withNavbar(navContent), conf.inner)))
  }

  def withNavbar(navLinks: Modifier*) =
    divClass(s"$Navbar $NavbarDefault")(
      divContainer(
        divClass(NavbarHeader)(
          hamburgerButton,
          a(`class` := NavbarBrand, href := reverse.list())("Pics")
        ),
        divClass(s"$NavbarCollapse $Collapse")(
          navLinks
        )
      )
    )

  def basePage(conf: PageConf) = TagPage(
    html(lang := En)(
      head(
        titleTag(conf.title),
        meta(name := "viewport", content := "width=device-width, initial-scale=1.0"),
        cssLink("//netdna.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css"),
        cssLink("//netdna.bootstrapcdn.com/font-awesome/3.2.1/css/font-awesome.css"),
        cssLink("//ajax.googleapis.com/ajax/libs/jqueryui/1.10.4/themes/smoothness/jquery-ui.css"),
        cssLink(at("css/main.css")),
        conf.extraHeader,
        jsScript("//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"),
        jsScript("//ajax.googleapis.com/ajax/libs/jqueryui/1.10.4/jquery-ui.min.js"),
        jsScript("//netdna.bootstrapcdn.com/bootstrap/3.1.1/js/bootstrap.min.js"),
        deferredJsPath(jsName),
      ),
      body(`class` := conf.bodyClass)(
        section(
          conf.inner
        )
      )
    )
  )
}

case class PageConf(title: String,
                    extraHeader: Modifier = (),
                    bodyClass: String = "",
                    inner: Modifier = ())
