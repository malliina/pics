package com.malliina.pics.sockets

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import com.malliina.pics.sockets.PicMediator._
import com.malliina.play.models.Username
import com.malliina.play.ws.{ActorMeta, JsonActor}
import play.api.libs.json.JsValue
import play.api.mvc.RequestHeader

object PicMediator {

  def props() = Props(new PicMediator)

  sealed trait Message

  case class ClientJoined(client: PicClient) extends Message

  case class ClientLeft(ref: ActorRef) extends Message

  case class Broadcast(json: JsValue) extends Message

  case class Unicast(json: JsValue, to: Username) extends Message

  case class PicClient(out: ActorRef, username: Username)

}

class PicMediator extends Actor with ActorLogging {
  var clients: Set[PicClient] = Set.empty

  override def receive: Receive = {
    case ClientJoined(client) =>
      clients += client
      log.info(s"Client joined. Clients: ${clients.size}.")
    case ClientLeft(client) =>
      remove(client)
    case Broadcast(msg) =>
      clients.foreach(_.out ! msg)
    case Unicast(msg, to) =>
      clients.filter(_.username == to).foreach(_.out ! msg)
    case Terminated(client) =>
      remove(client)
  }

  def remove(ref: ActorRef): Unit = {
    clients = clients.filterNot(_.out == ref)
  }
}

object ClientSocket {
  def props(ctx: SocketContext) = Props(new ClientSocket(ctx))
}

class ClientSocket(ctx: SocketContext) extends JsonActor(ctx) {
  override def preStart(): Unit = {
    super.preStart()
    ctx.mediator ! ClientJoined(PicClient(ctx.out, ctx.user))
  }
}

case class SocketContext(out: ActorRef,
                         rh: RequestHeader,
                         user: Username,
                         mediator: ActorRef) extends ActorMeta
