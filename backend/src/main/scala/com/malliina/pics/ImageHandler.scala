package com.malliina.pics

import java.nio.file.{Files, Path}

import cats.effect.IO
import com.sksamuel.scrimage.ImmutableImage

object ImageHandlerIO {
  def apply(prefix: String, resizer: ImageResizerIO, storage: DataSourceIO) =
    new ImageHandlerIO(prefix, resizer, storage)
}

class ImageHandlerIO(prefix: String, resizer: ImageResizerIO, val storage: DataSourceIO)
  extends ImageService[IO] {
  def createTempFile = IO(Files.createTempFile(prefix, null))

  override def handle(original: Path, key: Key): IO[Path] =
    IO(ImmutableImage.loader().fromPath(original)).flatMap { image =>
      handleImage(image, key)
    }

  override def handleImage(image: ImmutableImage, key: Key) = createTempFile.flatMap { dest =>
    resizer.resize(image, dest).flatMap { e =>
      e.fold(err => IO.raiseError(err.ioe), _ => storage.saveBody(key, dest).map(_ => dest))
    }
  }

  override def remove(key: Key): IO[PicResult] = storage.remove(key)
}

trait ImageHandlerLike[F[_]] {
  def handleImage(image: ImmutableImage, key: Key): F[Path]
  def remove(key: Key): F[PicResult]
}

trait ImageService[F[_]] extends ImageHandlerLike[F] {

  /** Might fail with Exception, ImageParseException, IllegalArgumentException, ...
    *
    * @param original orig image
    * @param key      desired key
    * @return
    */
  def handle(original: Path, key: Key): F[Path]
}
