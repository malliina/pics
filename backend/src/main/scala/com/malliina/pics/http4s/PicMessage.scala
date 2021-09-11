package com.malliina.pics.http4s

import com.malliina.pics.{PicOwner, PicsAdded, PicsRemoved, ProfileInfo}
import io.circe.*
import io.circe.syntax.EncoderOps

sealed trait PicMessage:
  def forUser(user: PicOwner): Boolean

sealed trait UnicastMessage extends PicMessage:
  def target: PicOwner
  override def forUser(user: PicOwner): Boolean = user == target

object PicMessage:
  private val pingJson = Json.obj("event" -> "ping".asJson)
  implicit val json: Encoder[PicMessage] = (msg: PicMessage) =>
    msg match
      case AddedMessage(pics, _)   => pics.asJson
      case RemovedMessage(pics, _) => pics.asJson
      case PingBroadcast           => pingJson
      case Welcome(info)           => info.asJson
  case object PingBroadcast extends PicMessage:
    def forUser(user: PicOwner): Boolean = true
  case class AddedMessage(pics: PicsAdded, owner: PicOwner) extends UnicastMessage:
    override def target = owner
  case class RemovedMessage(pics: PicsRemoved, owner: PicOwner) extends UnicastMessage:
    override def target = owner
  case class Welcome(info: ProfileInfo) extends UnicastMessage:
    override def target = info.user

  val ping = PingBroadcast

  def welcome(user: PicOwner, readOnly: Boolean) = Welcome(ProfileInfo(user, readOnly))
