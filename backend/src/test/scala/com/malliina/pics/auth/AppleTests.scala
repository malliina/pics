package com.malliina.pics.auth

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.malliina.http.OkClient
import com.malliina.web.JWTKeys
import tests.{BaseSuite, ClientSuite}

class AppleTests extends BaseSuite with ClientSuite:
  test("read apple conf".ignore) {
    val keys: IO[JWTKeys] = client().getAs[JWTKeys](AppleAuthFlow.jwksUri)
    val ks = keys.unsafeRunSync()
    println(ks)
  }
