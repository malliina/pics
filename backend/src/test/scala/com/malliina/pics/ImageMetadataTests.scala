package com.malliina.pics

import com.sksamuel.scrimage.metadata.ImageMetadata
import munit.FunSuite

import java.nio.file.Paths

class ImageMetadataTests extends FunSuite:
  val picDir = Paths.get("backend/files")

  test("read image metadata".ignore) {
    val file = picDir.resolve("demo-original.jpeg")
    val meta = ImageMetadata.fromPath(file)
    meta.tags().toList.foreach { t =>
      println(t)
    }
  }
