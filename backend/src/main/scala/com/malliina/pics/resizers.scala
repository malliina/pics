package com.malliina.pics

import java.io.{IOException, InputStream}
import java.nio.file.{Files, Path}
import cats.effect.Sync
import cats.syntax.all.*
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.util.AppLogger
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter

object ScrimageResizer:
  private val log = AppLogger(getClass)

  def Small[F[_]: Sync] = ScrimageResizer(400, 300)
  def Medium[F[_]: Sync] = ScrimageResizer(1440, 1080)
  def Large[F[_]: Sync] = ScrimageResizer(2880, 2160)

class ScrimageResizer[F[_]: Sync](maxWidth: Int, maxHeight: Int) extends ImageResizer[F]:
  override def resize(
    image: ImmutableImage,
    dest: Path
  ): F[Either[ImageException, StorageSize]] = recovered {
    val timed = Util.timed {
      image.bound(maxWidth, maxHeight).output(JpegWriter.Default, dest)
    }
    val resizedSize = Files.size(dest).bytes
    ScrimageResizer.log.info(
      s"Resized image with bounds ${maxWidth}x${maxHeight}px and size $resizedSize to $dest in ${timed.duration}."
    )
    resizedSize
  }

class AsIsResizer[F[_]: Sync] extends ImageResizer[F]:
  override def resize(image: ImmutableImage, dest: Path): F[Either[ImageException, StorageSize]] =
    recovered {
      image.output(JpegWriter.Default, dest)
      Files.size(dest).bytes
    }

object ImageResizer:
  private val log = AppLogger(getClass)

trait ImageResizer[F[_]: Sync] extends ImageResizerT[F]:
  def recovered[T](work: => T): F[Either[ImageException, T]] =
    Sync[F]
      .delay(work)
      .redeemWith(
        {
          case ioe: IOException =>
            Sync[F].delay {
              ImageResizer.log.error("Failed to resize image", ioe)
              Left(ImageException(ioe))
            }
          case e => Sync[F].raiseError(e)
        },
        t => Sync[F].pure(Right(t))
      )

trait ImageResizerT[F[_]]:
  def resizeStream(src: InputStream, dest: Path): F[Either[ImageException, StorageSize]] =
    resize(ImmutableImage.loader().fromStream(src), dest)

  def resizeFile(src: Path, dest: Path): F[Either[ImageException, StorageSize]] =
    resize(ImmutableImage.loader().fromPath(src), dest)

  def resize(image: ImmutableImage, dest: Path): F[Either[ImageException, StorageSize]]
