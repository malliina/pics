package com.malliina.pics

import java.io.{IOException, InputStream}
import java.nio.file.{Files, Path}

import com.malliina.storage.{StorageLong, StorageSize}
import com.sksamuel.scrimage.Image
import com.sksamuel.scrimage.nio.JpegWriter

object ScrimageResizer {
  def apply(maxWidth: Int, maxHeight: Int) = new ScrimageResizer(maxWidth, maxHeight)

  val Small = ScrimageResizer(400, 300)
  val Medium = ScrimageResizer(1440, 1080)
  val Large = ScrimageResizer(2880, 2160)
}

class ScrimageResizer(maxWidth: Int, maxHeight: Int) extends ImageResizer {
  def resizeStream(src: InputStream, dest: Path): Either[ImageException, StorageSize] =
    resize(Image.fromStream(src), dest)

  def resizeFile(src: Path, dest: Path): Either[ImageException, StorageSize] =
    resize(Image.fromPath(src), dest)

  def resize(image: Image, dest: Path): Either[ImageException, StorageSize] =
    try {
      image.bound(maxWidth, maxHeight).output(dest)(JpegWriter.Default)
      Right(Files.size(dest).bytes)
    } catch {
      case e: IOException => Left(ImageException(e))
    }

  def *(factor: Double) = ScrimageResizer((maxWidth * factor).toInt, (maxHeight * factor).toInt)
}

trait ImageResizer {
  def resizeFile(src: Path, dest: Path): Either[ImageException, StorageSize]
}

object PicsResizer {
  val default = new PicsResizer
}

class PicsResizer {
  def resize(original: Path): Either[ImageException, PicBundle] = {
    def temp(prefix: String) = Files.createTempFile(prefix, null)

    val small = temp("small")
    val medium = temp("medium")
    val large = temp("large")
    for {
      _ <- ScrimageResizer.Small.resizeFile(original, small)
      _ <- ScrimageResizer.Medium.resizeFile(original, medium)
      _ <- ScrimageResizer.Large.resizeFile(original, large)
    } yield PicBundle(small, medium, large, original)

  }
}
