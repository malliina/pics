package com.malliina.pics.js

import com.malliina.pics.{HtmlBuilder, Pics}
import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement}
import org.scalajs.dom.{Event, Node}
import org.scalajs.jquery.jQuery
import play.api.libs.json.JsValue

import scala.scalajs.js

@js.native
trait Popovers extends js.Object {
  @js.native
  def popover(): Unit = js.native
}

class PicsSocket extends BaseSocket("/sockets") {
  val document = dom.document

  object jsHtml extends HtmlBuilder(scalatags.JsDom)

  document.getElementsByClassName(jsHtml.CopyButton).foreach { node =>
    copyListen(node)
  }

  val popovers = jQuery("[data-toggle='popover']").asInstanceOf[Popovers]
  popovers.popover()

  def copyListen(node: Node): Unit =
    node.addEventListener("click", (e: Event) => {
      log.info("Copying...")
      val url = e.target.asInstanceOf[HTMLElement].getAttribute(jsHtml.dataIdAttr.name)
      copyToClipboard(url)
    })

  override def handlePayload(payload: JsValue): Unit = {
    handleValidated[Pics](payload) { pics =>
      log.info(s"Socket says $pics")
      val gallery = document.getElementById(jsHtml.galleryId)
      pics.pics.foreach { pic =>
        val newElem = jsHtml.thumbnail(pic).render
        copyListen(newElem)
        gallery.insertBefore(newElem, gallery.firstChild)
      }
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
