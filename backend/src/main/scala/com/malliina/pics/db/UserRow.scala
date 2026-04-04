package com.malliina.pics.db

import com.malliina.pics.{CognitoUserId, Language, PicUsername, Role}
import com.malliina.values.Email

import java.time.Instant

case class UserRow(
  id: Int,
  username: PicUsername,
  email: Option[Email],
  cognito: Option[CognitoUserId],
  role: Role,
  language: Language,
  added: Instant
)
