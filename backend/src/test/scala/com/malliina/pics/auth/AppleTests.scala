package com.malliina.pics.auth

import cats.effect.IO
import com.malliina.pics.PicsConf
import com.malliina.web.JWTKeys
import tests.ClientSuite

import java.time.Instant

class AppleTests extends munit.CatsEffectSuite with ClientSuite:
  test("read apple conf".ignore):
    client()
      .getAs[JWTKeys](AppleAuthFlow.jwksUri)
      .map: ks =>
        println(ks)

  test("create sign in with apple token".ignore):
    PicsConf
      .loadF[IO]
      .map: conf =>
        val siwa = SignInWithApple(conf.apple.get)
        val token = siwa.signInWithAppleToken(Instant.now())
        println(token)
