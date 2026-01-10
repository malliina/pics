package com.malliina.pics.auth

import com.malliina.pics.PicOwner
import com.malliina.values.Username
import com.malliina.web.JWTUser

object JWTUsers:
  def anon = user(Username.unsafe(PicOwner.anon.name))
  def user(user: Username) = new JWTUser:
    override def username: Username = user
