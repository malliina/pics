package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.auth.PicsAuth
import com.malliina.pics.sockets.ClientSockets.Unicast
import com.malliina.pics.sockets.{ClientSocket, ClientSockets, SocketContext}
import com.malliina.pics.{PicMeta, Pics}
import com.malliina.play.models.Username
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket

object Sockets {
  def apply(auth: PicsAuth, as: ActorSystem, mat: Materializer): Sockets =
    new Sockets(auth)(as, mat)
}

class Sockets(auth: PicsAuth)(implicit actorSystem: ActorSystem, mat: Materializer) extends PicSink {
  val mediator = actorSystem.actorOf(ClientSockets.props())

  def onPics(pics: Pics, owner: Username): Unit =
    mediator ! Unicast(Json.toJson(pics), owner)

  def listen = WebSocket.acceptOrResult[JsValue, JsValue] { rh =>
    auth.auth(rh).map(_.map { authedRequest =>
      ActorFlow.actorRef { out =>
        ClientSocket.props(SocketContext(out, rh, authedRequest.user, mediator))
      }
    })
  }
}

trait PicSink {
  def onPics(pics: Pics, owner: Username): Unit

  def onPic(pic: PicMeta, owner: Username): Unit =
    onPics(Pics(Seq(pic)), owner)
}
