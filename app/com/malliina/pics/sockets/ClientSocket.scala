package com.malliina.pics.sockets

import akka.actor.{ActorLogging, ActorRef, Props}
import com.malliina.pics.sockets.ClientSockets._
import com.malliina.pics.{PicRequest, ProfileInfo}
import com.malliina.play.ws.{ActorMeta, JsonActor}
import play.api.libs.json.Json

object ClientSocket {
  def props(ctx: SocketContext) = Props(new ClientSocket(ctx))
}

class ClientSocket(ctx: SocketContext) extends JsonActor(ctx) with ActorLogging {
  override def preStart(): Unit = {
    super.preStart()
    ctx.mediator ! ClientJoined(PicClient(ctx.out, ctx.user))
    out ! Json.toJson(ProfileInfo(ctx.user, ctx.req.readOnly))
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info("Stopped client.")
  }
}

case class SocketContext(out: ActorRef,
                         req: PicRequest,
                         mediator: ActorRef) extends ActorMeta {
  override def rh = req.rh

  def user = req.name
}
