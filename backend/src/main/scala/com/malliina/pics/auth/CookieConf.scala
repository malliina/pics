package com.malliina.pics.auth

case class CookieConf(
  user: String,
  session: String,
  returnUri: String,
  lastId: String,
  provider: String,
  prompt: String
)

object CookieConf:
  val pics =
    CookieConf("picsUser", "picsState", "picsReturnUri", "picsLastId", "picsProvider", "picsPrompt")
