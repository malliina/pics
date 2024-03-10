package com.malliina.pics

import cats.effect.Sync
import cats.syntax.all.*
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.util.AppLogger
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import fs2.io.file.{Files, Path}

import java.io.{IOException, InputStream}

object ScrimageResizer:
  private val log = AppLogger(getClass)

  def Small[F[_]: Sync: Files] = ScrimageResizer(400, 300)
  def Medium[F[_]: Sync: Files] = ScrimageResizer(1440, 1080)
  def Large[F[_]: Sync: Files] = ScrimageResizer(2880, 2160)

class ScrimageResizer[F[_]: Sync: Files](maxWidth: Int, maxHeight: Int) extends ImageResizer[F]:
  override def resize(
    image: ImmutableImage,
    dest: Path
  ): F[Either[ImageException, StorageSize]] = recovered:
    for
      timed <- S.delay:
        Util.timed:
          image.bound(maxWidth, maxHeight).output(JpegWriter.Default, dest.toNioPath)
      bytes <- Files[F].size(dest)
      resizedSize = bytes.bytes
      _ <- S.delay(
        ScrimageResizer.log.info(
          s"Resized image with bounds ${maxWidth}x${maxHeight}px and size $resizedSize to $dest in ${timed.duration}."
        )
      )
    yield resizedSize

class AsIsResizer[F[_]: Sync: Files] extends ImageResizer[F]:
  override def resize(image: ImmutableImage, dest: Path): F[Either[ImageException, StorageSize]] =
    recovered:
      for
        _ <- Sync[F].delay(image.output(JpegWriter.Default, dest.toNioPath))
        size <- Files[F].size(dest)
      yield size.bytes

object ImageResizer:
  private val log = AppLogger(getClass)

trait ImageResizer[F[_]: Sync] extends ImageResizerT[F]:
  val S = Sync[F]

  def recovered[T](work: => F[T]): F[Either[ImageException, T]] =
    work
      .redeemWith(
        {
          case ioe: IOException =>
            S.delay {
              ImageResizer.log.error("Failed to resize image", ioe)
              Left(ImageException(ioe))
            }
          case e => S.raiseError(e)
        },
        t => S.pure(Right(t))
      )

trait ImageResizerT[F[_]]:
  def resizeStream(src: InputStream, dest: Path): F[Either[ImageException, StorageSize]] =
    resize(ImmutableImage.loader().fromStream(src), dest)

  def resizeFile(src: Path, dest: Path): F[Either[ImageException, StorageSize]] =
    resize(ImmutableImage.loader().fromPath(src.toNioPath), dest)

  def resize(image: ImmutableImage, dest: Path): F[Either[ImageException, StorageSize]]
