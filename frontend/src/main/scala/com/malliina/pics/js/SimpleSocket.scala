package com.malliina.pics.js

import com.malliina.http.FullUrl
import com.malliina.pics.js.SocketEvent.{Close, Error, Message, Open}
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.scalajs.dom
import org.scalajs.dom.{CloseEvent, ErrorEvent, Event, MessageEvent, WebSocket}

trait EventLike:
  def event: Event

enum SocketEvent extends EventLike:
  case Open(event: Event, socket: SimpleSocket)
  case Message(event: MessageEvent)
  case Close(event: CloseEvent)
  case Error(event: ErrorEvent)

class SimpleSocket(val url: FullUrl):
  private val socket: dom.WebSocket = openSocket(url)

  private def openSocket(url: FullUrl): WebSocket =
    val socket = new dom.WebSocket(url.url)
    socket.onopen = (e: Event) => onEvent(Open(e, this))
    socket.onmessage = (e: MessageEvent) => onEvent(Message(e))
    socket.onclose = (e: CloseEvent) => onEvent(Close(e))
    socket.onerror = (e: ErrorEvent) => onEvent(Error(e))
    socket

  def isOpen = socket.readyState == WebSocket.OPEN

  def onEvent(event: SocketEvent): Unit = ()

  def send[T: Encoder](payload: T): Unit =
    val asString = payload.asJson.noSpaces
    socket.send(asString)
