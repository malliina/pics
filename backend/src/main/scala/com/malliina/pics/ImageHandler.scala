package com.malliina.pics

import java.nio.file.{Files, Path}

import cats.effect.IO
import com.malliina.concurrent.Execution.cached
import com.malliina.pics.ImageHandler.log
import com.sksamuel.scrimage.ImmutableImage
import play.api.Logger

import scala.concurrent.Future

object ImageHandler {
  private val log = Logger(getClass)

  def apply(prefix: String, resizer: ImageResizer, storage: DataSource) =
    new ImageHandler(prefix, resizer, storage)
}

class ImageHandler(prefix: String, resizer: ImageResizer, val storage: DataSource)
  extends ImageService[Future] {

  def createTempFile = Files.createTempFile(prefix, null)

  def handle(original: Path, key: Key): Future[Path] =
    Future(ImmutableImage.loader().fromPath(original)).flatMap { image =>
      handleImage(image, key)
    }

  def handleImage(image: ImmutableImage, key: Key): Future[Path] = {
    log.info(s"Handling $prefix of '$key'...")
    val dest = createTempFile
    resizer.resize(image, dest).flatMap { e =>
      e.fold(err => Future.failed(err.ioe), _ => storage.saveBody(key, dest).map(_ => dest))
    }
  }

  override def remove(key: Key): Future[PicResult] = storage.remove(key)
}

object ImageHandlerIO {
  def apply(prefix: String, resizer: ImageResizerIO, storage: ImageSourceLike[IO]) =
    new ImageHandlerIO(prefix, resizer, storage)
}

class ImageHandlerIO(prefix: String, resizer: ImageResizerIO, source: ImageSourceLike[IO])
  extends ImageService[IO] {
  def createTempFile = IO(Files.createTempFile(prefix, null))

  override def handle(original: Path, key: Key): IO[Path] =
    IO(ImmutableImage.loader().fromPath(original)).flatMap { image =>
      handleImage(image, key)
    }

  override def handleImage(image: ImmutableImage, key: Key) = createTempFile.flatMap { dest =>
    resizer.resize(image, dest).flatMap { e =>
      e.fold(err => IO.raiseError(err.ioe), _ => source.saveBody(key, dest).map(_ => dest))
    }
  }

  override def remove(key: Key): IO[PicResult] = source.remove(key)
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
