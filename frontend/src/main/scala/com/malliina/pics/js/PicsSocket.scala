package com.malliina.pics.js

import com.malliina.pics.*
import io.circe.*
import org.scalajs.dom
import org.scalajs.dom.*

import scala.concurrent.duration.DurationDouble
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.timers.*

class PicsSocket extends BaseSocket("/sockets"):
  val jsHtml = BaseHtml
  val document = dom.document

  var isReadOnly = false

  installCopyListeners(document.body)
  PicsJS.csrf.installCsrf(document.body)

  val popovers: Map[String, Popover] = document
    .querySelectorAll("[data-bs-toggle='popover']")
    .map { e =>
      e.getAttribute(jsHtml.dataIdAttr.name) -> new Popover(e, PopoverOptions.manual)
    }
    .toMap

  // hides popovers on outside click
  document.body.addEventListener(
    "click",
    (e: Event) =>
      val isPopover = e.target.isInstanceOf[HTMLElement] && Option(
        e.target.asInstanceOf[HTMLElement].getAttribute("data-bs-original-title")
      ).isDefined
      if !isPopover then popovers.values.foreach(_.hide())
  )

  def installCopyListeners(parent: Element): Unit =
    parent.getElementsByClassName(jsHtml.CopyButton).foreach { node =>
      addClickListener(node)
    }

  def addClickListener(node: Node): Unit =
    node.addEventListener(
      "click",
      (e: Event) =>
        log.info("Copying...")
        val htmlElem = e.target.asInstanceOf[HTMLElement]
        val url = htmlElem.getAttribute(jsHtml.dataIdAttr.name)
        copyToClipboard(url)
//        popovers.values.foreach(_.show())
        popovers.get(url).foreach { p =>
          p.show()
        }
    )

  override def handlePayload(payload: Json): Unit =
    val result = payload.hcursor.downField(PicsJson.EventKey).as[String].flatMap {
      case PicsAdded.Added =>
        payload.as[PicsAdded].map { pics =>
          pics.pics.foreach(prepend)
        }
      case PicsRemoved.Removed =>
        payload.as[PicsRemoved].map { keys =>
          keys.keys.foreach(remove)
        }
      case ProfileInfo.Welcome =>
        payload.as[ProfileInfo].map { profile =>
          onProfile(profile)
        }
      case other =>
        Left(DecodingFailure(s"Unknown '${PicsJson.EventKey}' value: '$other'.", Nil))
    }
    result.fold(err => log.info(s"Failed to parse '$payload': '$err'."), _ => ())

  def prepend(pic: BaseMeta) =
    elemById(jsHtml.galleryId).map { gallery =>
      val newElem =
        jsHtml.thumbnail(pic, readOnly = isReadOnly, visible = false, lazyLoaded = false).render
      installListeners(newElem)
      gallery.insertBefore(newElem, gallery.firstChild)
      // enables the transition
      setTimeout(0.1.seconds) {
        newElem.classList.remove("invisible")
      }
    }.getOrElse {
      fill(jsHtml.picsId, jsHtml.gallery(Seq(pic), readOnly = isReadOnly, lazyLoaded = false))
      elemById(jsHtml.galleryId).foreach(installListeners)
    }

  def installListeners(elem: Element): Unit =
    installCopyListeners(elem)
    PicsJS.csrf.installCsrf(elem)

  import jsHtml.tags.impl.all.*

  def remove(key: Key): Unit =
    for
      gallery <- elemById(jsHtml.galleryId)
      thumb <- Option(gallery.querySelector(s"[data-id='$key']"))
    yield
      gallery.removeChild(thumb)
      if Option(gallery.firstChild).isEmpty then fill(jsHtml.picsId, jsHtml.noPictures)

  def onProfile(info: ProfileInfo): Unit =
    isReadOnly = info.readOnly

  def fill(id: String, content: Frag): Unit =
    elemById(id).foreach { elem =>
      removeChildren(elem)
      elem.appendChild(content.render)
    }

  def elemById(id: String): Option[Element] = Option(document.getElementById(id))

  def removeChildren(elem: Element): Unit =
    while Option(elem.firstChild).isDefined do elem.removeChild(elem.firstChild)

  /** http://stackoverflow.com/a/30905277
    *
    * @param text
    *   to copy
    */
  def copyToClipboard(text: String): Unit =
    // Create a "hidden" input
    val aux = dom.document.createElement("input").asInstanceOf[HTMLInputElement]
    // Assign it the value of the supplied parameter
    aux.setAttribute("value", text)
    // Append it to the body
    document.body.appendChild(aux)
    // Highlight its content
    aux.select()
    // Copy the highlighted text
    document.execCommand("copy")
    // Remove it from the body
    document.body.removeChild(aux)
    log.info("Copied.")
