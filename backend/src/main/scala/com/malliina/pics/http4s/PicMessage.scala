package com.malliina.pics.http4s

import com.malliina.pics.{PicOwner, PicsAdded, PicsRemoved}
import com.malliina.play.json.JsonMessages
import play.api.libs.json.{JsValue, Json, Writes}

sealed trait PicMessage {
  def forUser(user: PicOwner): Boolean
}

object PicMessage {
  implicit val json: Writes[PicMessage] = Writes[PicMessage] {
    case AddedMessage(pics, _)   => Json.toJson(pics)
    case RemovedMessage(pics, _) => Json.toJson(pics)
    case Broadcast(json)         => json
  }
  case class Broadcast(message: JsValue) extends PicMessage {
    def forUser(user: PicOwner): Boolean = true
  }
  case class AddedMessage(pics: PicsAdded, owner: PicOwner) extends PicMessage {
    def forUser(user: PicOwner): Boolean = user == owner
  }
  case class RemovedMessage(pics: PicsRemoved, owner: PicOwner) extends PicMessage {
    def forUser(user: PicOwner): Boolean = user == owner
  }

  val ping = Broadcast(JsonMessages.ping)
}
