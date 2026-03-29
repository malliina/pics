package com.malliina.pics.http4s

import com.malliina.pics.{PicUsername, PicsAdded, PicsJson, PicsRemoved, ProfileInfo}
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}

sealed trait PicMessage:
  def forUser(user: PicUsername): Boolean

sealed trait UnicastMessage extends PicMessage:
  def target: PicUsername
  override def forUser(user: PicUsername): Boolean = user == target

object PicMessage:
  private val pingJson = Json.obj(PicsJson.EventKey -> "ping".asJson)
  given Encoder[PicMessage] =
    case AddedMessage(pics, _)   => pics.asJson
    case RemovedMessage(pics, _) => pics.asJson
    case PingBroadcast           => pingJson
    case Welcome(info)           => info.asJson
  case object PingBroadcast extends PicMessage:
    def forUser(user: PicUsername): Boolean = true
  case class AddedMessage(pics: PicsAdded, owner: PicUsername) extends UnicastMessage:
    override def target: PicUsername = owner
  case class RemovedMessage(pics: PicsRemoved, owner: PicUsername) extends UnicastMessage:
    override def target: PicUsername = owner
  case class Welcome(info: ProfileInfo) extends UnicastMessage:
    override def target: PicUsername = info.user

  val ping = PingBroadcast

  def welcome(user: PicUsername, readOnly: Boolean) = Welcome(ProfileInfo(user, readOnly))
