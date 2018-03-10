package com.malliina.pics.auth

import com.malliina.pics.PicOwner
import com.malliina.play.models.Email

object JWTUser {
  def anon = new JWTUser {
    override def username: PicOwner = PicOwner.anon
  }
}

trait JWTUser {
  def username: PicOwner
}

case class CognitoUser(username: PicOwner,
                       email: Option[Email],
                       groups: Seq[String],
                       verified: Verified) extends JWTUser
