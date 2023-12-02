package com.malliina.pics.auth

import com.malliina.config.ConfigReadable

case class SecretKey(value: String) extends AnyVal:
  override def toString = "****"

object SecretKey:
  val dev = SecretKey("app-jwt-signing-secret-goes-here-must-be-sufficiently-long")
  given ConfigReadable[SecretKey] = ConfigReadable.string.map(apply)
  