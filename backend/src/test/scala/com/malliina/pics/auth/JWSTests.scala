package com.malliina.pics.auth

import com.malliina.values.Username
import munit.FunSuite
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import scala.concurrent.duration.DurationInt

class JWSTests extends FunSuite:
  val secretKey = SecretKey("top-secret-secret-at-least-256-bits")

  case class MyData(username: Username)

  object MyData:
    implicit val json: Codec[MyData] = deriveCodec[MyData]

  test("sign-verify") {
    val in = """{"a": "b"}"""
    val jwt = JWS(secretKey)
    val token = jwt.sign(in)
    val res = jwt.verify(token)
    assertEquals(res.toOption.get, in)
  }

  test("jwt") {
    val jwt = JWT(secretKey)
    val expected = MyData(Username("Hi"))
    val signed = jwt.sign(expected, 1.hour)
    val actual = jwt.verify[MyData](signed)
    assert(actual.isRight)
    assertEquals(actual.toOption.get, expected)
  }
