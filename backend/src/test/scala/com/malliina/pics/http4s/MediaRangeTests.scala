package com.malliina.pics.http4s

import munit.FunSuite
import org.http4s.MediaRange

class MediaRangeTests extends FunSuite:
  test("parse media query") {
    val in =
      "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
    val ranges = in.split(",")
    val res = MediaRange.parse(ranges.head)
  }
