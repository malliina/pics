package com.malliina.pics.auth

import com.malliina.http.OkClient
import com.malliina.play.auth.JWTKeys
import tests.BaseSuite

class AppleTests extends BaseSuite {
  test("read apple conf".ignore) {
    val http = OkClient.default
    val keys = http.getAs[JWTKeys](AppleCodeValidator.jwksUri)
    val ks = await(keys)
    println(ks)
  }
}
