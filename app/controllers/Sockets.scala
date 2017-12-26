package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics._
import com.malliina.pics.auth.PicsAuth
import com.malliina.pics.sockets.ClientSockets.Unicast
import com.malliina.pics.sockets.{ClientSocket, ClientSockets, SocketContext}
import com.malliina.play.models.Username
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket

object Sockets {
  def apply(auth: PicsAuth, as: ActorSystem, mat: Materializer): Sockets =
    new Sockets(auth)(as, mat)
}

class Sockets(auth: PicsAuth)(implicit actorSystem: ActorSystem, mat: Materializer) extends PicSink {
  val mediator = actorSystem.actorOf(ClientSockets.props())

  def onPics(pics: ClientPics, owner: Username): Unit =
    unicast(pics, owner)

  def onPicsRemoved(keys: PicKeys, owner: Username): Unit =
    unicast(keys, owner)

  def unicast[C: Writes](c: C, to: Username): Unit =
    mediator ! Unicast(Json.toJson(c), to)

  def listen = WebSocket.acceptOrResult[JsValue, JsValue] { rh =>
    auth.auth(rh).map(_.map { authedRequest =>
      ActorFlow.actorRef { out =>
        ClientSocket.props(SocketContext(out, rh, authedRequest.user, mediator))
      }
    })
  }
}

trait PicSink {
  def onPics(pics: ClientPics, owner: Username): Unit

  def onPic(pic: ClientPicMeta, owner: Username): Unit =
    onPics(ClientPics(Seq(pic)), owner)

  def onPicsRemoved(keys: PicKeys, owner: Username): Unit

  def onPicRemoved(key: Key, owner: Username): Unit =
    onPicsRemoved(PicKeys(Seq(key)), owner)
}
