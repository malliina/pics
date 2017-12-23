package com.malliina.pics.sockets

import akka.actor.{Actor, ActorRef, Props}
import com.malliina.pics.Pics
import com.malliina.play.models.Username

object ClientSocket {
  def props(out: ActorRef, user: Username) = Props(new ClientSocket(out, user))
}

class ClientSocket(out: ActorRef, user: Username) extends Actor {
  override def receive: Receive = {
    case ps: Pics => out ! ps
  }
}
