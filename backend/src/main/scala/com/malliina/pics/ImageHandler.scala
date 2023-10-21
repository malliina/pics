package com.malliina.pics

import java.nio.file.{Files, Path}
import cats.effect.Sync
import cats.syntax.all.*
import com.sksamuel.scrimage.ImmutableImage

class ImageHandler[F[_]: Sync](
  prefix: String,
  resizer: ImageResizer[F],
  val storage: DataSourceT[F]
) extends ImageService[F]:
  val F = Sync[F]
  def createTempFile = F.blocking(Files.createTempFile(prefix, null))

  override def handle(original: Path, key: Key): F[Path] =
    F.blocking(ImmutableImage.loader().fromPath(original))
      .flatMap: image =>
        handleImage(image, key)

  override def handleImage(image: ImmutableImage, key: Key) = createTempFile.flatMap: dest =>
    resizer
      .resize(image, dest)
      .flatMap: e =>
        e.fold(err => F.raiseError(err.ioe), _ => storage.saveBody(key, dest).map(_ => dest))

  override def remove(key: Key): F[PicResult] = storage.remove(key)

trait ImageHandlerLike[F[_]]:
  def handleImage(image: ImmutableImage, key: Key): F[Path]
  def remove(key: Key): F[PicResult]

trait ImageService[F[_]] extends ImageHandlerLike[F]:

  /** Might fail with Exception, ImageParseException, IllegalArgumentException, ...
    *
    * @param original
    *   orig image
    * @param key
    *   desired key
    * @return
    */
  def handle(original: Path, key: Key): F[Path]
