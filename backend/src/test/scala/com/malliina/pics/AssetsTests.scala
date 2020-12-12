package com.malliina.pics

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import munit.FunSuite
import org.apache.commons.codec.digest.DigestUtils

class AssetsTests extends FunSuite {
  test("caching".ignore) {
    val in = Files.readString(Paths.get("frontend/target/assets/fonts.css"), StandardCharsets.UTF_8)
    val md2 = DigestUtils.md2Hex(in)
    val md5 = DigestUtils.md5Hex(in)
    val sha1 = DigestUtils.sha1Hex(in)
    Seq(md2, md5, sha1) foreach println
  }
}
