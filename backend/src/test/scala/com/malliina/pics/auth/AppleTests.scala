package com.malliina.pics.auth

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.malliina.http.OkClient
import com.malliina.pics.PicsConf
import com.malliina.web.JWTKeys
import tests.{BaseSuite, ClientSuite}
import java.time.Instant

class AppleTests extends BaseSuite with ClientSuite:
  test("read apple conf".ignore) {
    val keys: IO[JWTKeys] = client().getAs[JWTKeys](AppleAuthFlow.jwksUri)
    val ks = keys.unsafeRunSync()
    println(ks)
  }

  test("create sign in with apple token".ignore) {
    val conf = PicsConf.unsafeLoad()
    val siwa = SignInWithApple(conf.apple)
    val token = siwa.signInWithAppleToken(Instant.now())
    println(token)
  }
