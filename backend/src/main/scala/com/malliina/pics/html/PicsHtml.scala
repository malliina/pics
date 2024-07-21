package com.malliina.pics.html

import com.malliina.html.HtmlTags.{defer as _, *}
import com.malliina.html.UserFeedback
import com.malliina.http.{CSRFConf, CSRFToken, FullUrl}
import com.malliina.live.LiveReload
import com.malliina.pics.assets.{FileAssets, HashedAssets}
import com.malliina.pics.html.PicsHtml.*
import com.malliina.pics.http4s.Reverse
import com.malliina.pics.{AssetsSource, html as _, *}
import org.http4s.Uri
import scalatags.Text.all.*

import scala.language.implicitConversions

object PicsHtml:
  val CopyButton = "copy-button"

  val False = "false"
  val True = "true"

  private val KeyKey = "key"
  val dataIdAttr = attr("data-id")
  val dataContentAttr = attr("data-content")
  val async = attr("async").empty

  val reverse = Reverse

  def build(isProd: Boolean, csrf: CSRFConf): PicsHtml =
    val appScripts =
      if isProd then Seq(FileAssets.frontend_js)
      else Seq(FileAssets.frontend_js, FileAssets.frontend_loader_js, FileAssets.main_js)
    val externalScripts = if isProd then Nil else FullUrl.build(LiveReload.script).toSeq
    PicsHtml(
      appScripts,
      externalScripts,
      Seq(FileAssets.frontend_css, FileAssets.fonts_css, FileAssets.styles_css),
      AssetsSource(isProd),
      csrf
    )

  def postableForm(onAction: String, more: Modifier*) =
    form(role := FormRole, action := onAction, method := Post, more)

class PicsHtml(
  scripts: Seq[String],
  absoluteScripts: Seq[FullUrl],
  cssFiles: Seq[String],
  assets: AssetsSource,
  csrf: CSRFConf
) extends BaseHtml(csrf):
  private val authHtml = AuthHtml(assets, csrf)

  def signIn(feedback: Option[UserFeedback] = None) = basePage(authHtml.signIn(feedback))

  def signUp(feedback: Option[UserFeedback] = None) = basePage(authHtml.signUp(feedback))

  def profile = basePage(authHtml.profile)

  def drop(
    created: Option[PicMeta],
    feedback: Option[UserFeedback],
    user: BaseRequest,
    csrfToken: CSRFToken
  ) =
    val content =
      divContainer(
        renderFeedback(feedback),
        fullRow(
          postableForm(reverse.sync.renderString, `class` := "drop-row form-inline")(
            submitButton(`class` := btn.info)("Sync")
          )
        ),
        fullRow(
          postableForm(
            reverse.delete.renderString,
            `class` := "drop-row form-inline",
            id := "delete-form"
          )(
            csrfInput(csrfToken),
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
        created.fold(empty): key =>
          Seq[Modifier]("Saved ", a(href := key.url)(key.key.key))
      )
    val conf = PageConf("Pics - Drop", bodyClass = DropClass, inner = content)
    baseIndex("drop", if user.readOnly then None else Option(user.name), conf)

  def pics(
    urls: Seq[PicMeta],
    feedback: Option[UserFeedback],
    user: BaseRequest,
    limits: Limits,
    csrfToken: CSRFToken
  ) =
    val content = Seq(
      divClass("pics")(renderFeedback(feedback)),
      picsContent(urls, user.readOnly, csrfToken),
      pageNav(limits)
    )
    val conf = PageConf("Pics", bodyClass = PicsClass, inner = content)
    baseIndex("pics", if user.readOnly then None else Option(user.name), conf)

  private def pageNav(current: Limits) =
    val prev = current.prev.map(l => move(l.offset))
    val next = move(current.next.offset)
    val prevExtra = if prev.isRight then "" else " disabled"
    nav(aria.label := "Navigation", `class` := "d-flex justify-content-center py-3")(
      ul(`class` := "pagination")(
        li(`class` := s"page-item $prevExtra")(
          a(`class` := "page-link", prev.map(p => href := p).getOrElse(href := "#"))("Previous")
        ),
        li(`class` := "page-item")(a(`class` := "page-link", href := next)("Next"))
      )
    )

  private def move(offset: NonNeg) =
    Reverse.list.withQueryParams(Map(Limits.Offset -> offset.value))

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
    val content = divContainer(
      rowColumn(s"${col.md.six} top-padding")(
        message.fold(empty): msg =>
          alertSuccess(msg)
      )
    )
    basePage(PageConf("Goodbye!", inner = content))

  private def baseIndex(tabName: String, user: Option[PicOwner], conf: PageConf) =
    def navItem(thisTabName: String, tabId: String, url: Uri, faName: String) =
      val itemClass = if tabId == tabName then "nav-item active" else "nav-item"
      li(`class` := itemClass)(a(href := url, `class` := "nav-link")(fa(faName), s" $thisTabName"))

    val navContent = user
      .map: u =>
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
      .getOrElse:
        modifier(
          ulClass(s"${navbars.Nav} $MrAuto")(),
          ulClass(navbars.Nav)(
            navItem("Sign In", "signin", reverse.signIn, "arrow-right-to-bracket")
          )
        )
    basePage(conf.copy(inner = modifier(withNavbar(navContent), conf.inner)))

  private def fa(faName: String) =
    i(`class` := s"nav-icon $faName", title := faName, aria.hidden := tags.True)

  private def withNavbar(navLinks: Modifier*) =
    navbar.basic(
      reverse.list,
      "Pics",
      navLinks,
      navClass = s"${navbars.Navbar} navbar-expand-sm ${navbars.Light} ${navbars.BgLight}"
    )

  private def basePage(conf: PageConf) =
    html(lang := En)(
      head(
        meta(charset := "utf-8"),
        titleTag(conf.title),
        deviceWidthViewport,
        link(
          rel := "shortcut icon",
          `type` := "image/png",
          href := inlineOrAsset(FileAssets.img.pics_favicon_png)
        ),
        cssFiles.map: file =>
          cssLink(at(file)),
        conf.extraHeader,
        scripts.map: js =>
          deferredJsPath(js),
        absoluteScripts.map: url =>
          script(src := url, defer)
      ),
      body(`class` := conf.bodyClass)(
        section(
          conf.inner
        )
      )
    )

  private def deferredJsPath(path: String) =
    script(`type` := "application/javascript", src := at(path), defer)

  private def inlineOrAsset(path: String) =
    HashedAssets.dataUris.getOrElse(path, at(path).toString)
  private def at(path: String) = assets.at(path)

case class PageConf(
  title: String,
  extraHeader: Modifier = (),
  bodyClass: String = "",
  inner: Modifier = ()
)
