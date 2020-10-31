package com.malliina.pics

import java.io.{IOException, InputStream}
import java.nio.file.{Files, Path}

import com.malliina.concurrent.Execution.cached
import com.malliina.pics.ScrimageResizer.log
import com.malliina.storage.{StorageLong, StorageSize}
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import play.api.Logger

import scala.concurrent.Future

object ScrimageResizer {
  private val log = Logger(getClass)

  def apply(maxWidth: Int, maxHeight: Int) = new ScrimageResizer(maxWidth, maxHeight)

  val Small = ScrimageResizer(400, 300)
  val Medium = ScrimageResizer(1440, 1080)
  val Large = ScrimageResizer(2880, 2160)
}

class ScrimageResizer(maxWidth: Int, maxHeight: Int) extends ImageResizer {
  def resize(image: ImmutableImage, dest: Path): Future[Either[ImageException, StorageSize]] =
    recovered {
      val timed = Util.timed {
        image.bound(maxWidth, maxHeight).output(JpegWriter.Default, dest)
      }
      val resizedSize = Files.size(dest).bytes
      log.info(
        s"Resized image with bounds ${maxWidth}x${maxHeight}px and size $resizedSize to $dest in ${timed.duration}."
      )
      resizedSize
    }

  def *(factor: Double): ScrimageResizer =
    ScrimageResizer((maxWidth * factor).toInt, (maxHeight * factor).toInt)
}

object AsIsResizer extends ImageResizer {
  override def resize(
    image: ImmutableImage,
    dest: Path
  ): Future[Either[ImageException, StorageSize]] =
    recovered {
      image.output(JpegWriter.Default, dest)
      Files.size(dest).bytes
    }
}

object ImageResizer {
  private val log = Logger(getClass)
}

trait ImageResizer {

  import ImageResizer.log

  def resizeStream(src: InputStream, dest: Path): Future[Either[ImageException, StorageSize]] =
    resize(ImmutableImage.loader().fromStream(src), dest)

  def resizeFile(src: Path, dest: Path): Future[Either[ImageException, StorageSize]] =
    resize(ImmutableImage.loader().fromPath(src), dest)

  def resizeFileF(src: Path, dest: Path): Future[StorageSize] =
    resizeFile(src, dest).flatMap { e =>
      e.fold(err => Future.failed(err.ioe), size => fut(size))
    }

  def resize(image: ImmutableImage, dest: Path): Future[Either[ImageException, StorageSize]]

  def recovered[T](work: => T): Future[Either[ImageException, T]] = {
    Future(Right(work)).recover {
      case ioe: IOException =>
        log.error("Failed to resize image", ioe)
        Left(ImageException(ioe))
    }
  }

  def fut[T](t: T) = Future.successful(t)
}
