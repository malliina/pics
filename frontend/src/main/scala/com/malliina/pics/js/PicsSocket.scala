package com.malliina.pics.js

import com.malliina.pics._
import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement}
import org.scalajs.dom.{Element, Event, Node}
import play.api.libs.json.{JsError, JsValue}

import scala.concurrent.duration.DurationDouble
import scala.scalajs.js
import scala.scalajs.js.timers._

@js.native
trait Popovers extends js.Object {
  def popover(): Unit = js.native
  def popover(in: String): Unit = js.native
}

class PicsSocket extends BaseSocket("/sockets") {
  val jsHtml = BaseHtml
  val document = dom.document

  var isReadOnly = false

  installCopyListeners(document.body)
  PicsJS.csrf.installCsrf(document.body)

  val popovers = MyJQuery("[data-toggle='popover']").asInstanceOf[Popovers]
  popovers.popover()

  // hides popovers on outside click
  document.body.addEventListener(
    "click",
    (e: Event) => {
      val isPopover = e.target.isInstanceOf[HTMLElement] && Option(
        e.target.asInstanceOf[HTMLElement].getAttribute("data-original-title")
      ).isDefined
      if (!isPopover) {
        MyJQuery("[data-original-title]").asInstanceOf[Popovers].popover("hide")
      }
    }
  )

  def installCopyListeners(parent: Element): Unit =
    parent.getElementsByClassName(jsHtml.CopyButton).foreach { node =>
      addClickListener(node)
    }

  def addClickListener(node: Node): Unit =
    node.addEventListener(
      "click",
      (e: Event) => {
        log.info("Copying...")
        val url = e.target.asInstanceOf[HTMLElement].getAttribute(jsHtml.dataIdAttr.name)
        copyToClipboard(url)
      }
    )

  override def handlePayload(payload: JsValue): Unit = {
    val result = (payload \ PicsJson.EventKey).validate[String].flatMap {
      case PicsAdded.Added =>
        payload.validate[PicsAdded].map { pics =>
          pics.pics.foreach(prepend)
        }
      case PicsRemoved.Removed =>
        payload.validate[PicsRemoved].map { keys =>
          keys.keys.foreach(remove)
        }
      case ProfileInfo.Welcome =>
        payload.validate[ProfileInfo].map { profile =>
          onProfile(profile)
        }
      case other =>
        JsError(s"Unknown '${PicsJson.EventKey}' value: '$other'.")
    }
    result.fold(err => log.info(s"Failed to parse '$payload': '$err'."), _ => ())
  }

  def prepend(pic: BaseMeta) =
    elemById(jsHtml.galleryId)
      .map { gallery =>
        val newElem =
          jsHtml.thumbnail(pic, readOnly = isReadOnly, visible = false, lazyLoaded = false).render
        installListeners(newElem)
        gallery.insertBefore(newElem, gallery.firstChild)
        // enables the transition
        setTimeout(0.1.seconds) {
          newElem.classList.remove("invisible")
        }
      }
      .getOrElse {
        fill(jsHtml.picsId, jsHtml.gallery(Seq(pic), readOnly = isReadOnly, lazyLoaded = false))
        elemById(jsHtml.galleryId).foreach(installListeners)
      }

  def installListeners(elem: Element): Unit = {
    installCopyListeners(elem)
    PicsJS.csrf.installCsrf(elem)
  }

  import jsHtml.tags.impl.all._

  def remove(key: Key): Unit =
    for {
      gallery <- elemById(jsHtml.galleryId)
      thumb <- Option(gallery.querySelector(s"[data-id='$key']"))
    } yield {
      gallery.removeChild(thumb)
      if (Option(gallery.firstChild).isEmpty) {
        fill(jsHtml.picsId, jsHtml.noPictures)
      }
    }

  def onProfile(info: ProfileInfo): Unit = {
    isReadOnly = info.readOnly
  }

  def fill(id: String, content: Frag): Unit =
    elemById(id).foreach { elem =>
      removeChildren(elem)
      elem.appendChild(content.render)
    }

  def elemById(id: String): Option[Element] = Option(document.getElementById(id))

  def removeChildren(elem: Element): Unit =
    while (Option(elem.firstChild).isDefined) elem.removeChild(elem.firstChild)

  /** http://stackoverflow.com/a/30905277
    *
    * @param text to copy
    */
  def copyToClipboard(text: String): Unit = {
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
  }
}
