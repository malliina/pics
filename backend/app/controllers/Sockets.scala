package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.malliina.concurrent.Execution.cached
import com.malliina.pics._
import com.malliina.pics.auth.PicsAuth
import com.malliina.pics.sockets.ClientSockets.Unicast
import com.malliina.pics.sockets.{ClientSocket, ClientSockets, SocketContext}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket

object Sockets {
  def apply(auth: PicsAuth, as: ActorSystem, mat: Materializer): Sockets =
    new Sockets(auth)(as, mat)
}

class Sockets(auth: PicsAuth)(implicit actorSystem: ActorSystem, mat: Materializer)
  extends PicSink {
  val mediator = actorSystem.actorOf(ClientSockets.props())

  def onPics(pics: ClientPics, owner: PicRequest): Unit =
    unicast(pics, owner)

  def onPicsRemoved(keys: PicKeys, owner: PicRequest): Unit =
    unicast(keys, owner)

  def unicast[C: Writes](c: C, to: PicRequest): Unit =
    mediator ! Unicast(Json.toJson(c), to.name)

  def listen = WebSocket.acceptOrResult[JsValue, JsValue] { rh =>
    auth
      .auth(rh)
      .map(_.map { user =>
        ActorFlow.actorRef { out =>
          ClientSocket.props(SocketContext(out, user, mediator))
        }
      })
  }
}

trait PicSink {
  def onPics(pics: ClientPics, owner: PicRequest): Unit

  def onPic(pic: ClientPicMeta, owner: PicRequest): Unit =
    onPics(ClientPics(Seq(pic)), owner)

  def onPicsRemoved(keys: PicKeys, owner: PicRequest): Unit

  def onPicRemoved(key: Key, owner: PicRequest): Unit =
    onPicsRemoved(PicKeys(Seq(key)), owner)
}
