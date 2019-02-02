package com.malliina.pics.sockets

import akka.actor.{ActorRef, Props}
import com.malliina.pics.sockets.ClientSocket.log
import com.malliina.pics.sockets.ClientSockets._
import com.malliina.pics.{PicRequest, ProfileInfo}
import com.malliina.play.ws.{ActorMeta, JsonActor}
import play.api.Logger
import play.api.libs.json.Json

object ClientSocket {
  private val log = Logger(getClass)

  def props(ctx: SocketContext) = Props(new ClientSocket(ctx))
}

class ClientSocket(ctx: SocketContext) extends JsonActor(ctx) {
  override def preStart(): Unit = {
    super.preStart()
    ctx.mediator ! ClientJoined(PicClient(ctx.out, ctx.user))
    out ! Json.toJson(ProfileInfo(ctx.user, ctx.req.readOnly))
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(s"Stopped client '${ctx.req.name}'.")
  }
}

case class SocketContext(out: ActorRef,
                         req: PicRequest,
                         mediator: ActorRef) extends ActorMeta {
  override def rh = req.rh

  def user = req.name
}
