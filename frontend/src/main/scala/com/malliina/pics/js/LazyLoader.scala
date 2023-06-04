package com.malliina.pics.js

import com.malliina.pics.PicsStrings
import org.scalajs.dom
import org.scalajs.dom.*

import scala.annotation.unused
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.Dynamic.literal

@js.native
trait IntersectionOptions extends js.Object:
  def rootMargin: String = js.native

object IntersectionOptions:
  def vertical(pixels: Int) = apply(s"${pixels}px 0px ${pixels}px 0px")

  def apply(rootMargin: String): IntersectionOptions =
    literal(rootMargin = rootMargin).asInstanceOf[IntersectionOptions]

@js.native
trait IntersectionObserverEntry extends js.Object:
  def isIntersecting: Boolean = js.native
  def target: dom.Element = js.native

@JSGlobal
@js.native
class IntersectionObserver(
  @unused init: js.Function2[js.Array[IntersectionObserverEntry], IntersectionObserver, ?],
  @unused options: IntersectionOptions
) extends js.Object:
  def observe(elem: Node): Unit = js.native
  def unobserve(elem: Node): Unit = js.native

object LazyLoader:
  def apply() = new LazyLoader(PicsStrings.Lazy)

/** https://developers.google.com/web/fundamentals/performance/lazy-loading-guidance/images-and-video/
  */
class LazyLoader(lazyClass: String):
  val document = dom.document
  document.addEventListener[Event](
    "DOMContentLoaded",
    (_: Event) =>
      val lazyImages = document.querySelectorAll(s"img.$lazyClass")
      val lazyImageObserver = new IntersectionObserver(
        (entries, observer) =>
          entries.foreach { e =>
            if e.isIntersecting then
              val lazyImage = e.target.asInstanceOf[HTMLImageElement]
              lazyImage.setAttribute("src", lazyImage.getAttribute("data-src"))
              lazyImage.classList.remove(lazyClass)
              lazyImage.classList.add(PicsStrings.Loaded)
              observer.unobserve(lazyImage)
          },
        IntersectionOptions.vertical(500)
      )
      lazyImages.foreach { n =>
        lazyImageObserver.observe(n)
      }
  )
