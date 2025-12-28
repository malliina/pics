package com.malliina.pics.js

import com.malliina.http.FullUrl
import com.malliina.pics.js.BaseSocket.{EventKey, Ping, wsBaseUrl}
import io.circe.*
import io.circe.parser.parse
import org.scalajs.dom
import org.scalajs.dom.MessageEvent

object BaseSocket:
  val EventKey = "event"
  val Ping = "ping"

  private def wsBaseUrl: FullUrl =
    val location = dom.window.location
    val wsProto = if location.protocol == "http:" then "ws" else "wss"
    FullUrl(wsProto, location.host, "")

class BaseSocket(wsPath: String, log: BaseLogger) extends SimpleSocket(wsBaseUrl.append(wsPath)):
  override def onEvent(event: SocketEvent): Unit = event match
    case SocketEvent.Open(event, _) => setFeedback("Connected to socket.")
    case SocketEvent.Message(event) => onMessage(event)
    case SocketEvent.Close(event)   => setFeedback("Connection closed.")
    case SocketEvent.Error(event)   => setFeedback("Connection errored.")

  def elem(id: String): dom.Element = dom.document.getElementById(id)

  def handlePayload(payload: Json): Unit = ()

  def handleValidated[T: Decoder](json: Json)(process: T => Unit): Unit =
    json.as[T].fold(err => onJsonFailure(err), process)

  def onMessage(msg: MessageEvent): Unit =
    log.info(s"Got message: ${msg.data.toString}")
    parse(msg.data.toString).fold(
      pf => onJsonException(pf),
      json =>
        val isPing = json.hcursor.downField(EventKey).as[String].toOption.contains(Ping)
        if !isPing then handlePayload(json)
    )

  def setFeedback(feedback: String): Unit =
    log.info(feedback)
  //    statusElem.innerHTML = feedback

  def onJsonException(f: ParsingFailure): Unit =
    log.info(s"Parsing failure $f")

  protected def onJsonFailure(result: DecodingFailure): Unit =
    log.info(s"JSON error $result")
