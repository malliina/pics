package com.malliina.play.auth

import com.malliina.pics.PicOwner
import com.malliina.values.Username
import com.malliina.web.JWTUser

object JWTUsers {
  def anon = new JWTUser {
    override def username: Username = Username(PicOwner.anon.name)
  }
}
