package com.malliina.pics.html

import com.malliina.html.HtmlTags.{fullUrl as _, *}
import com.malliina.html.UserFeedback
import com.malliina.http.FullUrl
import com.malliina.live.LiveReload
import com.malliina.pics.html.PicsHtml.*
import com.malliina.pics.http4s.Reverse
import com.malliina.pics.AssetsSource
import com.malliina.pics.{html as _, *}
import org.http4s.Uri
import scalatags.Text.all.{StringFrag as _, defer as _, *}

import scala.language.implicitConversions

object PicsHtml:
  val CopyButton = "copy-button"

  val False = "false"
  val True = "true"

  val KeyKey = "key"
  val dataIdAttr = attr("data-id")
  val dataContentAttr = attr("data-content")
  val async = attr("async").empty

  val reverse = Reverse

  def build(isProd: Boolean): PicsHtml =
    val opt = if isProd then "opt" else "fastopt"
    val assetPrefix = s"frontend-$opt"
    val appScripts =
      if isProd then Seq(s"$assetPrefix-bundle.js")
      else Seq(s"$assetPrefix-library.js", s"$assetPrefix-loader.js", s"$assetPrefix.js")
    val externalScripts = if isProd then Nil else FullUrl.build(LiveReload.script).toSeq
    new PicsHtml(
      appScripts,
      externalScripts,
      Seq(s"$assetPrefix.css", "fonts.css", "styles.css"),
      AssetsSource(isProd)
    )

  def postableForm(onAction: String, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)

class PicsHtml(
  scripts: Seq[String],
  absoluteScripts: Seq[FullUrl],
  cssFiles: Seq[String],
  assets: AssetsSource
) extends BaseHtml:
  private val authHtml = AuthHtml(assets)

  def signIn(feedback: Option[UserFeedback] = None) = basePage(authHtml.signIn(feedback))

  def signUp(feedback: Option[UserFeedback] = None) = basePage(authHtml.signUp(feedback))

  def profile = basePage(authHtml.profile)

  def drop(created: Option[PicMeta], feedback: Option[UserFeedback], user: BaseRequest) =
    val content =
      divContainer(
        renderFeedback(feedback),
        fullRow(
          postableForm(reverse.sync.renderString, `class` := "drop-row form-inline")(
//            CSRF.getToken(user.rh).fold(empty)(token => csrfInput(token.name, token.value)),
            submitButton(`class` := btn.info)("Sync")
          )
        ),
        fullRow(
          postableForm(
            reverse.delete.renderString,
            `class` := "drop-row form-inline",
            id := "delete-form"
          )(
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
    baseIndex("drop", if user.readOnly then None else Option(user.name), conf)

  def pics(urls: Seq[PicMeta], feedback: Option[UserFeedback], user: BaseRequest) =
    val content = Seq(divClass("pics")(renderFeedback(feedback)), picsContent(urls, user.readOnly))
    val conf = PageConf("Pics", bodyClass = PicsClass, inner = content)
    baseIndex("pics", if user.readOnly then None else Option(user.name), conf)

  def privacyPolicy =
    val privacyContent: Modifier = divContainer(
      h1(`class` := "page-header")("Privacy Policy"),
      p(
        "This privacy policy describes how your information is used and stored when you use this app."
      ),
      p(
        "The purpose of using and storing your information is to enable app functionality and optimize your user experience. Your information is only used to enable application functionality."
      ),
      p("Your information is not shared with third parties. The app includes no ads."),
      p(
        "Should you choose to authenticate, your email address is associated with any subsequent images you take while authenticated. The email address is only used to persist image ownership information. No other personal or sensitive information is stored."
      ),
      p(
        "Images are taken with the native camera APIs provided by the platform, which may embed additional data to image files."
      ),
      p(
        "Network communications: This app may communicate with other networked servers. The communication facilitates the transfer of images to and from your devices so that you can view public images and view your own images on multiple devices."
      ),
      p("Network requests may be logged by server software.")
    )
    basePage(PageConf("Pics - Privacy Policy", inner = privacyContent))

  def support =
    val content = divContainer(
      h1("Support"),
      p("For support issues:"),
      ul(
        li("Email us at ", a(href := "mailto:info@skogberglabs.com")("info@skogberglabs.com")),
        li("Open an issue on ", a(href := "https://github.com/malliina/pics-ios/issues")("GitHub"))
      )
    )
    basePage(PageConf("Pics - Support", inner = content))

  def eject(message: Option[String]) =
    val content =
      divContainer(
        rowColumn(s"${col.md.six} top-padding")(
          message.fold(empty) { msg =>
            alertSuccess(msg)
          }
        )
      )
    basePage(PageConf("Goodbye!", inner = content))

  def baseIndex(tabName: String, user: Option[PicOwner], conf: PageConf) =
    def navItem(thisTabName: String, tabId: String, url: Uri, faName: String) =
      val itemClass = if tabId == tabName then "nav-item active" else "nav-item"
      li(`class` := itemClass)(a(href := url, `class` := "nav-link")(fa(faName), s" $thisTabName"))

    val navContent = user.map { u =>
      modifier(
        ulClass(s"${navbars.Nav} $MrAuto")(
          navItem("Pics", "pics", reverse.list, "image"),
          navItem("Drop", "drop", reverse.drop, "upload")
        ),
        ulClass(s"${navbars.Nav}")(
          li(`class` := s"nav-item $Dropdown")(
            a(
              href := "#",
              `class` := s"nav-link $DropdownToggle",
              data("bs-toggle") := Dropdown,
              role := Button,
              aria.haspopup := tags.True,
              aria.expanded := tags.False
            )(
              fa("user"),
              s" ${u.name} ",
              spanClass(Caret)
            ),
            ulClass(DropdownMenu)(
              li(
                a(href := reverse.signOut, `class` := "nav-link")(
                  fa("arrow-right-from-bracket"),
                  " Sign Out"
                )
              )
            )
          )
        )
      )
    }.getOrElse {
      modifier(
        ulClass(s"${navbars.Nav} $MrAuto")(),
        ulClass(navbars.Nav)(
          navItem("Sign In", "signin", reverse.signIn, "arrow-right-to-bracket")
        )
      )
    }
    basePage(conf.copy(inner = modifier(withNavbar(navContent), conf.inner)))

  def fa(faName: String) =
    i(`class` := s"nav-icon $faName", title := faName, aria.hidden := tags.True)

  def withNavbar(navLinks: Modifier*) =
    navbar.basic(
      reverse.list,
      "Pics",
      navLinks,
      navClass = s"${navbars.Navbar} navbar-expand-sm ${navbars.Light} ${navbars.BgLight}"
    )

  def basePage(conf: PageConf) =
    html(lang := En)(
      head(
        meta(charset := "utf-8"),
        titleTag(conf.title),
        deviceWidthViewport,
        link(rel := "shortcut icon", `type` := "image/png", href := at("img/pics-favicon.png")),
        cssFiles.map(file => cssLink(at(file))),
        conf.extraHeader,
        scripts.map(js => deferredJsPath(js)),
        absoluteScripts.map { url =>
          script(src.:=(url)(urlAttr), defer)
        }
      ),
      body(`class` := conf.bodyClass)(
        section(
          conf.inner
        )
      )
    )

  def deferredJsPath(path: String) =
    script(`type` := "application/javascript", src := at(path), defer)

  def at(path: String) = assets.at(path)

case class PageConf(
  title: String,
  extraHeader: Modifier = (),
  bodyClass: String = "",
  inner: Modifier = ()
)
