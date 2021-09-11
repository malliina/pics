package com.malliina.pics.js

import com.malliina.pics.CSRFConf.{CsrfCookieName, CsrfTokenName}
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLFormElement
import org.scalajs.dom.{Element, Event}

object CSRFUtils:
  def apply() = new CSRFUtils()

class CSRFUtils(val log: BaseLogger = BaseLogger.console):
  val document = dom.document

  def installCsrf(parent: Element): Unit =
    parent.getElementsByTagName("form").foreach { node =>
      node.addEventListener(
        "submit",
        (e: Event) => installTo(e.target.asInstanceOf[HTMLFormElement])
      )
    }

  def installTo(form: HTMLFormElement) =
    readCookie(CsrfCookieName).map { tokenValue =>
      form.appendChild(BaseHtml.csrfInput(CsrfTokenName, tokenValue).render)
    }.getOrElse {
      log.info("CSRF token not found.")
    }

  def readCookie(key: String) =
    cookiesMap(document.cookie).get(key)

  def cookiesMap(in: String) =
    in.split(";")
      .toList
      .map(_.trim.split("=", 2).toList)
      .collect { case key :: value :: Nil =>
        key -> value
      }
      .toMap
