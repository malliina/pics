package com.malliina.pics.js

import com.malliina.pics.{CSRFConf, CSRFToken}
import com.malliina.values.{ErrorMessage, Readable}
import org.scalajs.dom
import org.scalajs.dom.{Element, Event, HTMLFormElement}

import java.util.NoSuchElementException

class CSRFUtils(csrf: CSRFConf, val log: BaseLogger = BaseLogger.console):
  val document = dom.document

  def installCsrf(parent: Element): Unit =
    parent
      .getElementsByTagName("form")
      .foreach: node =>
        node.addEventListener(
          "submit",
          (e: Event) => installTo(e.target.asInstanceOf[HTMLFormElement])
        )

  def installTo(form: HTMLFormElement) =
    csrfFromCookie
      .map: token =>
        val bh = BaseHtml(csrf)
        import bh.given
        form.appendChild(bh.csrfInput(token).render)
      .getOrElse:
        log.info("CSRF token not found.")

  def csrfFromCookie: Either[ErrorMessage, CSRFToken] =
    readCookie[CSRFToken](csrf.cookieName)

  def csrfFromCookieUnsafe = csrfFromCookie.fold(
    err => throw NoSuchElementException(err.message),
    identity
  )

  def readCookie[R](key: String)(using r: Readable[R]): Either[ErrorMessage, R] =
    cookiesMap(document.cookie)
      .get(key)
      .toRight(ErrorMessage(s"Cookie not found: '$key'."))
      .flatMap(str => r.read(str))

  private def cookiesMap(in: String) =
    in.split(";")
      .toList
      .map(_.trim.split("=", 2).toList)
      .collect:
        case key :: value :: Nil =>
          key -> value
      .toMap
