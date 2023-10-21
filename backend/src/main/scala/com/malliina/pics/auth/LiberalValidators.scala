package com.malliina.pics.auth

import com.malliina.web.{Issuer, LiberalValidator}

object LiberalValidators:
  val auth0 = LiberalValidator(KeyConfs.auth0, Issuer("https://malliina.eu.auth0.com/"))
