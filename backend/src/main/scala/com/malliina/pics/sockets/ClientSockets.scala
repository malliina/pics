package com.malliina.pics.sockets

import akka.actor.{Actor, ActorRef, Props, Terminated}
import com.malliina.pics.PicOwner
import com.malliina.pics.sockets.ClientSockets._
import play.api.Logger
import play.api.libs.json.JsValue

class ClientSockets extends Actor {
  var clients: Set[PicClient] = Set.empty

  override def receive: Receive = {
    case ClientJoined(client) =>
      context.watch(client.out)
      clients += client
      log.info(s"Client '${client.username}' joined. Clients: ${clients.size}.")
    case ClientLeft(client) =>
      remove(client)
    case Broadcast(msg) =>
      clients.foreach(_.out ! msg)
    case Unicast(msg, to) =>
      clients.filter(_.username == to).foreach(_.out ! msg)
    case Terminated(client) =>
      remove(client)
      log.info(s"Client left. Clients: ${clients.size}.")
  }

  def remove(ref: ActorRef): Unit = {
    clients = clients.filterNot(_.out == ref)
  }
}

object ClientSockets {
  private val log = Logger(getClass)

  def props() = Props(new ClientSockets)

  sealed trait Message

  case class ClientJoined(client: PicClient) extends Message

  case class ClientLeft(ref: ActorRef) extends Message

  case class Broadcast(json: JsValue) extends Message

  case class Unicast(json: JsValue, to: PicOwner) extends Message

  case class PicClient(out: ActorRef, username: PicOwner)

}
