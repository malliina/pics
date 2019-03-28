package com.malliina.pics.html

import com.malliina.html.UserFeedback
import com.malliina.http.FullUrl
import com.malliina.pics.html.PicsHtml._
import com.malliina.pics.{html => _, _}
import com.malliina.play.tags.TagPage
import com.malliina.play.tags.Tags._
import controllers.routes
import play.api.http.MimeTypes
import play.api.mvc.Call
import play.filters.csrf.CSRF
import scalatags.Text
import scalatags.Text.GenericAttr
import scalatags.Text.all._

import scala.language.implicitConversions

object PicsHtml {
  val CopyButton = "copy-button"
  val True = "true"
  val False = "false"
  val KeyKey = "key"
  val dataIdAttr = attr("data-id")
  val dataContentAttr = attr("data-content")
  val defer = attr("defer").empty
  val async = attr("async").empty

  val reverse = routes.PicsController
  implicit val urlAttr: GenericAttr[FullUrl] = new GenericAttr[FullUrl]
  implicit val callAttr: GenericAttr[Call] = new GenericAttr[Call]

  def build(isProd: Boolean): PicsHtml = {
    val name = "frontend"
    val opt = if (isProd) "opt" else "fastopt"
//    new PicsHtml(Seq(s"$name-$opt-library.js", s"$name-$opt-loader.js", s"$name-$opt.js"))
    new PicsHtml(Seq(s"$name-$opt.js"))
  }

  implicit def urlWriter(url: FullUrl): Text.StringFrag =
    stringFrag(url.url)

  implicit def userFrag(user: PicOwner): Text.StringFrag =
    stringFrag(user.name)

  def deferredJs(file: String) = deferredJsPath(s"js/$file")

  def deferredJsPath(path: String) = script(`type` := MimeTypes.JAVASCRIPT, src := at(path), defer)

  def deferredAsyncJs[V: AttrValue](v: V) =
    script(`type` := MimeTypes.JAVASCRIPT, src := v, defer, async)

  def at(file: String) = routes.PicsAssets.versioned(file)

  def postableForm(onAction: String, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)
}

class PicsHtml(scripts: Seq[String]) extends BaseHtml {
  def signIn(feedback: Option[UserFeedback] = None) = basePage(AuthHtml.signIn(feedback))

  def signUp(feedback: Option[UserFeedback] = None) = basePage(AuthHtml.signUp(feedback))

  def profile = basePage(AuthHtml.profile)

  def drop(created: Option[PicMeta], feedback: Option[UserFeedback], user: PicRequest) = {
    val content =
      divContainer(
        renderFeedback(feedback),
        fullRow(
          postableForm(reverse.sync().toString, `class` := "drop-row form-inline")(
            CSRF.getToken(user.rh).fold(empty)(token => csrfInput(token.name, token.value)),
            submitButton(`class` := btn.info)("Sync")
          )
        ),
        fullRow(
          postableForm(reverse.delete().toString,
                       `class` := "drop-row form-inline",
                       id := "delete-form")(
            divClass("input-group")(
              divClass("input-group-prepend")(
                spanClass("input-group-text")("pics/")
              ),
              input(`type` := "text", `class` := FormControl, name := KeyKey, placeholder := "key"),
              divClass("input-group-append")(
                submitButton(`class` := btnOutline.danger)("Delete")
              )
            )
          )
        ),
        div(`class` := "upload-drop-zone", id := "drop-zone")(
          strong("Drag files here")
        ),
        tag("progress")(id := "progress", min := "0", max := "100", value := "0")(0),
        div(id := "feedback"),
        created.fold(empty) { key =>
          Seq[Modifier]("Saved ", a(href := key.url)(key.key.key))
        }
      )
    val conf = PageConf("Pics - Drop", bodyClass = DropClass, inner = content)
    baseIndex("drop", if (user.readOnly) None else Option(user.name), conf)
  }

  def pics(urls: Seq[PicMeta], feedback: Option[UserFeedback], user: PicRequest) = {
    val content = Seq(divClass("pics")(renderFeedback(feedback)), picsContent(urls, user.readOnly))
    val conf = PageConf("Pics", bodyClass = PicsClass, inner = content)
    baseIndex("pics", if (user.readOnly) None else Option(user.name), conf)
  }

  def privacyPolicy = {
    val privacyContent: Modifier = divContainer(
      h1(`class` := "page-header")("Privacy Policy"),
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
        li("Email us at ", a(href := "mailto:info@skogberglabs.com")("info@skogberglabs.com")),
        li("Open an issue on ", a(href := "https://github.com/malliina/pics-ios/issues")("GitHub"))
      )
    )
    basePage(PageConf("Pics - Support", inner = content))
  }

  def eject(message: Option[String]) = {
    val content =
      divContainer(
        rowColumn(s"${col.md.six} top-padding")(
          message.fold(empty) { msg =>
            alertSuccess(msg)
          }
        )
      )
    basePage(PageConf("Goodbye!", inner = content))
  }

  def baseIndex(tabName: String, user: Option[PicOwner], conf: PageConf) = {
    def navItem(thisTabName: String, tabId: String, url: Call, faName: String) = {
      val itemClass = if (tabId == tabName) "nav-item active" else "nav-item"
      li(`class` := itemClass)(a(href := url, `class` := "nav-link")(fa(faName), s" $thisTabName"))
    }

    val navContent = user
      .map { u =>
        modifier(
          ulClass(s"${navbars.Nav} $MrAuto")(
            navItem("Pics", "pics", reverse.list(), "image"),
            navItem("Drop", "drop", reverse.drop(), "upload")
          ),
          ulClass(s"${navbars.Nav}")(
            li(`class` := s"nav-item $Dropdown")(
              a(href := "#",
                `class` := s"nav-link $DropdownToggle",
                dataToggle := Dropdown,
                role := Button,
                aria.haspopup := tags.True,
                aria.expanded := tags.False)(
                fa("user"),
                s" ${u.name} ",
                spanClass(Caret)
              ),
              ulClass(DropdownMenu)(
                li(a(href := routes.CognitoControl.signOut(), `class` := "nav-link")(fa("sign-in-alt"),
                                                                                     " Sign Out"))
              )
            )
          )
        )
      }
      .getOrElse {
        modifier(
          ulClass(s"${navbars.Nav} $MrAuto")(),
          ulClass(navbars.Nav)(
            navItem("Sign In", "signin", reverse.signIn(), "sign-out-alt")
          )
        )
      }
    basePage(conf.copy(inner = modifier(withNavbar(navContent), conf.inner)))
  }

  def fa(faName: String) = i(`class` := s"fa fa-$faName", title := faName, aria.hidden := tags.True)

  def withNavbar(navLinks: Modifier*) =
    navbar.basic(reverse.list(),
                 "Pics",
                 navLinks,
                 navClass =
                   s"${navbars.Navbar} navbar-expand-sm ${navbars.Light} ${navbars.BgLight}")

  def basePage(conf: PageConf) = TagPage(
    html(lang := En)(
      head(
        meta(charset := "utf-8"),
        titleTag(conf.title),
        deviceWidthViewport,
        link(rel := "shortcut icon", `type` := "image/png", href := at("img/pics-favicon.png")),
        cssLinkHashed(
          "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css",
          "sha384-Gn5384xqQ1aoWXA+058RXPxPg6fy4IWvTNh0E263XmFcJlSAwiGgFAW/dAiS6JXm"
        ),
        cssLinkHashed("https://use.fontawesome.com/releases/v5.8.1/css/all.css",
                      "sha384-50oBUHEmvpQ+1lW4y57PTFmhCaXp0ML5d60M1M7uH2+nqUivzIebhndOJK28anvf"
        ),
        cssLink("https://fonts.googleapis.com/css?family=Roboto:400,500"),
//        cssLink(at("css/main.css")),
        cssLink(at("fonts.css")),
        cssLink(at("styles.css")),
        conf.extraHeader,
        scripts.map { js =>
          deferredJsPath(js)
        }
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
