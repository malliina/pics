package com.malliina.play.auth

object LiberalValidators {
  val auth0 = new LiberalValidator(KeyConfs.auth0, Issuer("https://malliina.eu.auth0.com/"))
}
