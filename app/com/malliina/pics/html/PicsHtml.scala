package com.malliina.pics.html

import com.malliina.html.UserFeedback
import com.malliina.http.FullUrl
import com.malliina.pics.assets.AppAssets
import com.malliina.pics.html.PicsHtml._
import com.malliina.pics.{html => _, _}
import com.malliina.play.models.Username
import com.malliina.play.tags.PlayTags._
import com.malliina.play.tags.TagPage
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
  val async = attr("async").empty

  val reverse = routes.PicsController

  implicit def urlWriter(url: FullUrl): scalatags.Text.StringFrag =
    new scalatags.Text.StringFrag(url.url)

  implicit val urlAttr = new GenericAttr[FullUrl]

  def deferredJs(file: String) = deferredJsPath(s"js/$file")

  def deferredJsPath(path: String) = script(`type` := MimeTypes.JAVASCRIPT, src := at(path), defer)

  def deferredAsyncJs[V: AttrValue](v: V) = script(`type` := MimeTypes.JAVASCRIPT, src := v, defer, async)

  def at(file: String) = routes.PicsAssets.versioned(file)

  def postableForm(onAction: String, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)
}

class PicsHtml(jsName: String) extends HtmlBuilder(new com.malliina.html.Tags(scalatags.Text)) with LoginStrings {
  implicit def userFrag(user: PicOwner): Text.StringFrag = stringFrag(user.name)

  import tags._

  val novalidate = attr("novalidate").empty

  def socialButton(provider: String, linkTo: Call, linkText: String) =
    a(`class` := s"social-button $provider", href := linkTo)(
      span(`class` := s"social-logo $provider"), span(`class` := "social-text", linkText)
    )

  def signIn(feedback: Option[UserFeedback] = None) = {
    val heading = fullRow(h1("Sign In"))
    val socials = rowColumn(s"${col.md.twelve} social-container")(
      socialButton("google", routes.CognitoControl.google(), "Sign in with Google"),
      socialButton("facebook", routes.CognitoControl.facebook(), "Sign in with Facebook"),
      socialButton("amazon", routes.CognitoControl.amazon(), "Sign in with Amazon")
    )
    val loginDivider = divClass("login-divider")(
      divClass("line"),
      divClass("text")("or")
    )
    val loginForm = form(id := LoginFormId, `class` := col.md.twelve, method := Post, novalidate)(
      input(`type` := "hidden", name := "token", id := "token"),
      input(`type` := "hidden", name := "error", id := "error"),
      divClass(FormGroup)(
        labeledInput("Email address", EmailId, "email", Option("me@example.com"))
      ),
      divClass(FormGroup)(
        labeledInput("Password", PasswordId, "password", None)
      ),
      divClass(FormGroup)(
        submitButton(`class` := btn.primary, "Sign in")
      ),
      divClass(FormGroup)(
        renderFeedback(feedback)
      )
    )

    val conf = PageConf(
      "Pics - Login",
      bodyClass = "login",
      inner = divClass("container")(divClass("auth-form ml-auto mr-auto")(heading, socials, loginDivider, row(loginForm))),
      extraHeader = modifier(
        jsScript(AppAssets.js.aws_cognito_sdk_min_js),
        jsScript(AppAssets.js.amazon_cognito_identity_min_js)
      )
    )
    baseIndex("login", None, conf)
  }

  def labeledInput(labelText: String, inId: String, inType: String, maybePlaceholder: Option[String]) = modifier(
    label(`for` := inId)(labelText),
    input(`type` := inType, `class` := FormControl, id := inId, maybePlaceholder.fold(empty)(ph => placeholder := ph))
  )

  def drop(created: Option[PicMeta], feedback: Option[UserFeedback], user: PicRequest) = {
    val content =
      divContainer(
        renderFeedback(feedback),
        fullRow(
          postableForm(reverse.sync().toString, `class` := "drop-row form-inline")(
            submitButton(`class` := btn.info)("Sync")
          )
        ),
        fullRow(
          postableForm(reverse.delete().toString, `class` := "drop-row form-inline", id := "delete-form")(
            divClass("input-group")(
              divClass("input-group-prepend")(
                spanClass("input-group-text")("pics/")
              ),
              input(`type` := "text", `class` := FormControl, name := "key", placeholder := "key"),
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
    val conf = PageConf("Pics - Drop", bodyClass = "drop", inner = content)
    baseIndex("drop", if(user.readOnly) None else Option(user.name), conf)
  }

  def pics(urls: Seq[PicMeta], feedback: Option[UserFeedback], user: PicRequest) = {
    val content = Seq(divClass("pics")(renderFeedback(feedback)), picsContent(urls, user.readOnly))
    val conf = PageConf("Pics", bodyClass = "pics", inner = content)
    baseIndex("pics", if(user.readOnly) None else Option(user.name), conf)
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
    def navItem(thisTabName: String, tabId: String, url: Call, iconicName: String) = {
      val itemClass = if (tabId == tabName) "nav-item active" else "nav-item"
      li(`class` := itemClass)(a(href := url, `class` := "nav-link")(iconic(iconicName), s" $thisTabName"))
    }

    val navContent = user.map { u =>
      modifier(
        ulClass(s"${navbar.Nav} $MrAuto")(
          navItem("Pics", "pics", reverse.list(), "picture"),
          navItem("Drop", "drop", reverse.drop(), "upload")
        ),
        ulClass(s"${navbar.Nav}")(
          li(`class` := s"nav-item $Dropdown")(
            a(href := "#", `class` := s"nav-link $DropdownToggle", dataToggle := Dropdown, role := Button, aria.haspopup := tags.True, aria.expanded := tags.False)(
              iconic("person"), s" ${u.name} ", spanClass(Caret)
            ),
            ulClass(DropdownMenu)(
              li(a(href := routes.Admin.logout(), `class` := "nav-link")(iconic("account-logout"), " Sign Out"))
            )
          )
        )
      )
    }.getOrElse {
      modifier(
        ulClass(s"${navbar.Nav} $MrAuto")(),
        ulClass(navbar.Nav)(
          navItem("Sign In", "signin", reverse.signIn(), "account-login")
        )
      )
    }
    basePage(conf.copy(inner = modifier(withNavbar(navContent), conf.inner)))
  }

  def withNavbar(navLinks: Modifier*) =
    navbar.basic(reverse.list(), "Pics", navLinks, navClass = s"${navbar.Navbar} navbar-expand-sm ${navbar.Light} ${navbar.BgLight}")

  def basePage(conf: PageConf) = TagPage(
    html(lang := En)(
      head(
        meta(charset := "utf-8"),
        titleTag(conf.title),
        deviceWidthViewport,
        cssLinkHashed("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css", "sha384-Gn5384xqQ1aoWXA+058RXPxPg6fy4IWvTNh0E263XmFcJlSAwiGgFAW/dAiS6JXm"),
        cssLink("https://use.fontawesome.com/releases/v5.0.6/css/all.css"),
        cssLink("https://fonts.googleapis.com/css?family=Roboto:400,500"),
        cssLink(at("css/main.css")),
        conf.extraHeader,
        jsHashed("https://code.jquery.com/jquery-3.2.1.slim.min.js", "sha384-KJ3o2DKtIkvYIK3UENzmM7KCkRr/rE9/Qpg6aAZGJwFDMVNA/GpGFF93hXpG5KkN"),
        jsHashed("https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.12.9/umd/popper.min.js", "sha384-ApNbgh9B+Y1QKtv3Rn7W3mgPxhU9K/ScQsAP7hUibX39j7fakFPskvXusvfa0b4Q"),
        jsHashed("https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/js/bootstrap.min.js", "sha384-JZR6Spejh4U02d8jOt6vLEHfe/JQGiRRSQQxSfFWpi1MquVdAyjUar5+76PVCmYl"),
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
