package com.malliina.pics.auth

import com.malliina.pics.{Language, PicUsername, Role}

case class PicUser(username: PicUsername, role: Role, language: Language)

object JWTUsers:
  def anon = user(PicUsername.anon, Role.ReadOnly, Language.default)
  def user(user: PicUsername, role: Role, language: Language) = PicUser(user, role, language)
