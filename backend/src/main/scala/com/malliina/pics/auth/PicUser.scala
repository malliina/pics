package com.malliina.pics.auth

import com.malliina.pics.{Language, Role}
import io.circe.Codec

case class PicUser(
  user: UserPayload,
  role: Role,
  language: Language
) derives Codec.AsObject

object PicUser:
  def anon = PicUser(UserPayload.anon, Role.ReadOnly, Language.default)
