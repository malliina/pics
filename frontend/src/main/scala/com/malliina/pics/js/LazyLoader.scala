package com.malliina.pics.js

import com.malliina.pics.PicsStrings
import org.scalajs.dom
import org.scalajs.dom.raw._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
trait IntersectionObserverEntry extends js.Object {
  def isIntersecting: Boolean = js.native
  def target: dom.Element = js.native
}

@JSGlobal
@js.native
class IntersectionObserver(
    init: js.Function2[js.Array[IntersectionObserverEntry], IntersectionObserver, _])
    extends js.Object {
  def observe(elem: Node): Unit = js.native
  def unobserve(elem: Node): Unit = js.native
}

object LazyLoader {
  def apply() = new LazyLoader(PicsStrings.Lazy)
}

/**
  * https://developers.google.com/web/fundamentals/performance/lazy-loading-guidance/images-and-video/
  */
class LazyLoader(lazyClass: String) {
  val document = dom.document
  document.addEventListener[Event](
    "DOMContentLoaded",
    _ => {
      val lazyImages = document.querySelectorAll(s"img.$lazyClass")
      val lazyImageObserver = new IntersectionObserver((entries, observer) => {
        entries.foreach { e =>
          if (e.isIntersecting) {
            val lazyImage = e.target.asInstanceOf[HTMLImageElement]
            lazyImage.setAttribute("src", lazyImage.getAttribute("data-src"))
            lazyImage.classList.remove(lazyClass)
            observer.unobserve(lazyImage)
          }
        }
      })
      lazyImages.foreach { n =>
        lazyImageObserver.observe(n)
      }
    }
  )
}
