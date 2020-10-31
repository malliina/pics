package com.malliina.pics.auth

import munit.FunSuite

class JWSTests extends FunSuite {
  test("sign-verify") {
    val in = """{"a": "b"}"""
    val jwt = JWS("top-secret-secret-at-least-256-bits")
    val token = jwt.sign(in)
    val res = jwt.verify(token)
    assertEquals(res.toOption.get, in)
  }
}
