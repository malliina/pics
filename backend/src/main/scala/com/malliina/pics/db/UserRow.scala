package com.malliina.pics.db

import com.malliina.pics.{Language, PicUsername, Role}

import java.time.Instant

case class UserRow(
  id: Int,
  username: PicUsername,
  role: Role,
  language: Language,
  added: Instant
)
