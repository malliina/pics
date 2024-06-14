package com.malliina.pics

import cats.effect.Sync
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.sksamuel.scrimage.ImmutableImage
import fs2.io.file.{Files, Path}

class ImageHandler[F[_]: Sync: Files](
  prefix: String,
  resizer: ImageResizer[F],
  val storage: DataSourceT[F]
) extends ImageService[F]:
  val S = Sync[F]
  val F = Files[F]
  def createTempFile = F.createTempFile(None, prefix, ".tmp", None)

  override def handle(original: Path, key: Key): F[Path] =
    S.blocking(ImmutableImage.loader().fromPath(original.toNioPath))
      .flatMap: image =>
        handleImage(image, key)

  override def handleImage(image: ImmutableImage, key: Key) = createTempFile.flatMap: dest =>
    resizer
      .resize(image, dest)
      .flatMap: e =>
        e.fold(err => S.raiseError(err.ioe), _ => storage.saveBody(key, dest).map(_ => dest))

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
