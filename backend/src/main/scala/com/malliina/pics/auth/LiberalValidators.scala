package com.malliina.pics.auth

import com.malliina.web.LiberalValidator
import com.malliina.web.WebLiterals.issuer

object LiberalValidators:
  val auth0 = LiberalValidator(KeyConfs.auth0, issuer"https://malliina.eu.auth0.com/")
