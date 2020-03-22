package com.malliina.pics

import java.nio.file.{Files, Path}

import com.malliina.concurrent.Execution.cached
import com.malliina.pics.ImageHandler.log
import com.sksamuel.scrimage.Image
import play.api.Logger

import scala.concurrent.Future

object ImageHandler {
  private val log = Logger(getClass)

  def apply(prefix: String, resizer: ImageResizer, storage: DataSource) =
    new ImageHandler(prefix, resizer, storage)
}

class ImageHandler(prefix: String, resizer: ImageResizer, val storage: DataSource)
  extends ImageHandlerLike {

  def createTempFile = Files.createTempFile(prefix, null)

  def handleImage(image: Image, key: Key): Future[Path] = {
    log.info(s"Handling $prefix of '$key'...")
    val dest = createTempFile
    resizer.resize(image, dest).flatMap { e =>
      e.fold(err => Future.failed(err.ioe), _ => storage.saveBody(key, dest).map(_ => dest))
    }
  }

  override def remove(key: Key): Future[PicResult] = storage.remove(key)
}

trait ImageHandlerLike {

  /** Might fail with Exception, ImageParseException, IllegalArgumentException, ...
    *
    * @param original orig image
    * @param key      desired key
    * @return
    */
  def handle(original: Path, key: Key): Future[Path] =
    Future(Image.fromPath(original)).flatMap { image =>
      handleImage(image, key)
    }

  def handleImage(image: Image, key: Key): Future[Path]

  def remove(key: Key): Future[PicResult]
}
