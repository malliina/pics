package com.malliina.pics.js

import com.malliina.pics.{HtmlBuilder, PicMeta, Pics}
import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement}
import org.scalajs.dom.{Element, Event, Node}
import org.scalajs.jquery.jQuery
import play.api.libs.json.JsValue

import scala.concurrent.duration.DurationDouble
import scala.scalajs.js
import scala.scalajs.js.timers._

@js.native
trait Popovers extends js.Object {
  @js.native
  def popover(): Unit = js.native

  @js.native
  def popover(in: String): Unit = js.native
}

class PicsSocket extends BaseSocket("/sockets") {
  val document = dom.document

  object jsHtml extends HtmlBuilder(scalatags.JsDom)

  installCopyListeners(document.body)

  val popovers = jQuery("[data-toggle='popover']").asInstanceOf[Popovers]
  popovers.popover()

  // hides popovers on outside click
  document.body.addEventListener("click", (e: Event) => {
    val isPopover = e.target.isInstanceOf[HTMLElement] && Option(e.target.asInstanceOf[HTMLElement].getAttribute("data-original-title")).isDefined
    if (!isPopover) {
      jQuery("[data-original-title]").asInstanceOf[Popovers].popover("hide")
    }
  })

  def installCopyListeners(parent: Element): Unit =
    parent.getElementsByClassName(jsHtml.CopyButton).foreach { node =>
      addClickListener(node)
    }

  def addClickListener(node: Node): Unit =
    node.addEventListener("click", (e: Event) => {
      log.info("Copying...")
      val url = e.target.asInstanceOf[HTMLElement].getAttribute(jsHtml.dataIdAttr.name)
      copyToClipboard(url)
    })

  override def handlePayload(payload: JsValue): Unit = {
    handleValidated[Pics](payload) { pics =>
      log.info(s"Socket says $pics")
      pics.pics.foreach { pic =>
        prepend(pic)
      }
    }
  }

  def prepend(pic: PicMeta) = {
    val gallery = document.getElementById(jsHtml.galleryId)
    val newElem = jsHtml.thumbnail(pic, visible = false).render
    installCopyListeners(newElem)
    gallery.insertBefore(newElem, gallery.firstChild)
    // enables the transition
    setTimeout(0.1.seconds) {
      newElem.classList.remove("invisible")
    }
  }

  /** http://stackoverflow.com/a/30905277
    *
    * @param text
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
