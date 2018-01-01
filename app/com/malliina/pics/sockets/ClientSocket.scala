package com.malliina.pics.sockets

import akka.actor.{ActorLogging, ActorRef, Props}
import com.malliina.pics.PicOwner
import com.malliina.pics.sockets.ClientSockets._
import com.malliina.play.ws.{ActorMeta, JsonActor}
import play.api.mvc.RequestHeader

object ClientSocket {
  def props(ctx: SocketContext) = Props(new ClientSocket(ctx))
}

class ClientSocket(ctx: SocketContext) extends JsonActor(ctx) with ActorLogging {
  override def preStart(): Unit = {
    super.preStart()
    ctx.mediator ! ClientJoined(PicClient(ctx.out, ctx.user))
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info("Stopped client.")
  }
}

case class SocketContext(out: ActorRef,
                         rh: RequestHeader,
                         user: PicOwner,
                         mediator: ActorRef) extends ActorMeta
