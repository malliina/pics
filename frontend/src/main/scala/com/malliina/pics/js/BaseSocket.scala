package com.malliina.pics.js

import com.malliina.http.FullUrl
import com.malliina.pics.js.BaseSocket.{EventKey, Ping}
import org.scalajs.dom
import org.scalajs.dom.{CloseEvent, Event, MessageEvent, WebSocket}
import io.circe.*
import io.circe.syntax.EncoderOps
import io.circe.parser.parse

object BaseSocket:
  val EventKey = "event"
  val Ping = "ping"

class BaseSocket(wsPath: String, val log: BaseLogger = BaseLogger.console):
  val socket: dom.WebSocket = openSocket(wsPath)

  def elem(id: String): dom.Element = dom.document.getElementById(id)

  def handlePayload(payload: Json): Unit = ()

  def handleValidated[T: Decoder](json: Json)(process: T => Unit): Unit =
    json.as[T].fold(err => onJsonFailure(err), process)

  def showConnected(): Unit =
    setFeedback("Connected to socket.")

  def showDisconnected(): Unit =
    setFeedback("Connection closed.")

  def send[T: Encoder](payload: T): Unit =
    val asString = payload.asJson.noSpaces
    socket.send(asString)

  def onMessage(msg: MessageEvent): Unit =
    log.info(s"Got message: ${msg.data.toString}")
    parse(msg.data.toString).fold(
      pf => onJsonException(pf),
      json =>
        val isPing = json.hcursor.downField(EventKey).as[String].toOption.contains(Ping)
        if !isPing then handlePayload(json)
    )

  def onConnected(e: Event): Unit = showConnected()

  def onClosed(e: CloseEvent): Unit = showDisconnected()

  def onError(e: Event): Unit = showDisconnected()

  def openSocket(pathAndQuery: String): WebSocket =
    val url = wsBaseUrl.append(pathAndQuery)
    val socket = new dom.WebSocket(url.url)
    socket.onopen = (e: Event) => onConnected(e)
    socket.onmessage = (e: MessageEvent) => onMessage(e)
    socket.onclose = (e: CloseEvent) => onClosed(e)
    socket.onerror = (e: Event) => onError(e)
    socket

  def wsBaseUrl: FullUrl =
    val location = dom.window.location
    val wsProto = if location.protocol == "http:" then "ws" else "wss"
    FullUrl(wsProto, location.host, "")

  def setFeedback(feedback: String): Unit =
    log.info(feedback)
  //    statusElem.innerHTML = feedback

  def onJsonException(f: ParsingFailure): Unit =
    log.info(s"Parsing failure $f")

  protected def onJsonFailure(result: DecodingFailure): Unit =
    log.info(s"JSON error $result")
