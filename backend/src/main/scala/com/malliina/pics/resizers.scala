package com.malliina.pics

import java.io.{IOException, InputStream}
import java.nio.file.{Files, Path}

import cats.effect.IO
import com.malliina.concurrent.Execution.cached
import com.malliina.pics.ScrimageResizer.log
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.util.AppLogger
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter

import scala.concurrent.Future

object ScrimageResizerIO {
  private val log = AppLogger(getClass)

  def apply(maxWidth: Int, maxHeight: Int): ScrimageResizerIO =
    new ScrimageResizerIO(maxWidth, maxHeight)

  val Small = ScrimageResizerIO(400, 300)
  val Medium = ScrimageResizerIO(1440, 1080)
  val Large = ScrimageResizerIO(2880, 2160)
}

class ScrimageResizerIO(maxWidth: Int, maxHeight: Int) extends ImageResizerIO {
  override def resize(
    image: ImmutableImage,
    dest: Path
  ): IO[Either[ImageException, StorageSize]] = recovered {
    val timed = Util.timed {
      image.bound(maxWidth, maxHeight).output(JpegWriter.Default, dest)
    }
    val resizedSize = Files.size(dest).bytes
    ScrimageResizerIO.log.info(
      s"Resized image with bounds ${maxWidth}x${maxHeight}px and size $resizedSize to $dest in ${timed.duration}."
    )
    resizedSize
  }
}

object AsIsResizerIO extends ImageResizerIO {
  override def resize(image: ImmutableImage, dest: Path): IO[Either[ImageException, StorageSize]] =
    recovered {
      image.output(JpegWriter.Default, dest)
      Files.size(dest).bytes
    }
}

object ImageResizerIO {
  val log = AppLogger(getClass)
}

trait ImageResizerIO extends ImageResizerT[IO] {
  def recovered[T](work: => T): IO[Either[ImageException, T]] =
    IO(work).redeemWith(
      {
        case ioe: IOException =>
          IO {
            ImageResizerIO.log.error("Failed to resize image", ioe)
            Left(ImageException(ioe))
          }
        case e => IO.raiseError(e)
      },
      t => IO.pure(Right(t))
    )
}

object ScrimageResizer {
  private val log = AppLogger(getClass)

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
  private val log = AppLogger(getClass)
}

trait ImageResizer extends ImageResizerT[Future] {
  import ImageResizer.log

  def resizeFileF(src: Path, dest: Path): Future[StorageSize] =
    resizeFile(src, dest).flatMap { e =>
      e.fold(err => Future.failed(err.ioe), size => fut(size))
    }

  def recovered[T](work: => T): Future[Either[ImageException, T]] =
    Future(Right(work)).recover { case ioe: IOException =>
      log.error("Failed to resize image", ioe)
      Left(ImageException(ioe))
    }

  def fut[T](t: T) = Future.successful(t)
}

trait ImageResizerT[F[_]] {
  def resizeStream(src: InputStream, dest: Path): F[Either[ImageException, StorageSize]] =
    resize(ImmutableImage.loader().fromStream(src), dest)

  def resizeFile(src: Path, dest: Path): F[Either[ImageException, StorageSize]] =
    resize(ImmutableImage.loader().fromPath(src), dest)

  def resize(image: ImmutableImage, dest: Path): F[Either[ImageException, StorageSize]]
}
