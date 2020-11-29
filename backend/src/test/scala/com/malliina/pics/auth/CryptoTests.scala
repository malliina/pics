package com.malliina.pics.auth

import munit.FunSuite

import scala.util.Random

class CryptoTests extends FunSuite {
  test("generate random string") {
    val str = Random.alphanumeric.take(20).mkString("")
    assertEquals(str.length, 20)
    println(str)
  }
}
