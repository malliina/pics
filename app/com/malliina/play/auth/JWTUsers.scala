package com.malliina.play.auth

import com.malliina.pics.PicOwner
import com.malliina.play.models.Username

object JWTUsers {
  def anon = new JWTUser {
    override def username: Username = Username(PicOwner.anon.name)
  }
}
