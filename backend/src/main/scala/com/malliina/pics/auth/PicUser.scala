package com.malliina.pics.auth

import com.malliina.pics.{Language, PicUsername, Role}
import io.circe.Codec

case class PicUser(username: PicUsername, role: Role, language: Language) derives Codec.AsObject

object PicUser:
  def anon = user(PicUsername.anon, Role.ReadOnly, Language.default)
  def user(user: PicUsername, role: Role, language: Language) = PicUser(user, role, language)
