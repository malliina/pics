package com.malliina.pics

import akka.stream.Materializer
import com.malliina.concurrent.Execution.cached
import com.malliina.pics.s3.AsyncS3Bucket
import com.sksamuel.scrimage.Image

import scala.concurrent.Future

object MultiSizeHandler {
  def cached(name: String, origin: DataSource, mat: Materializer) =
    FileCachingPics(FilePics.named(name, mat), origin)

  def default(mat: Materializer): MultiSizeHandler = {
    new MultiSizeHandler(
      ImageHandler("small", ScrimageResizer.Small, cached("smalls", AsyncS3Bucket.Small, mat)),
      ImageHandler("medium", ScrimageResizer.Medium, cached("mediums", AsyncS3Bucket.Medium, mat)),
      ImageHandler("large", ScrimageResizer.Large, cached("larges", AsyncS3Bucket.Large, mat)),
      ImageHandler("original", AsIsResizer, cached("originals", AsyncS3Bucket.Original, mat))
    )
  }

  def clones(handler: ImageHandler) = new MultiSizeHandler(
    handler, handler, handler, handler
  )
}

class MultiSizeHandler(val smalls: ImageHandler,
                       val mediums: ImageHandler,
                       val larges: ImageHandler,
                       val originals: ImageHandler) extends ImageHandlerLike {
  val handlers = Seq(smalls, mediums, larges, originals)

  override def handleImage(image: Image, key: Key) = {
    val work = Future.traverse(handlers)(_.handleImage(image, key))
    work.flatMap { paths =>
      paths.headOption
        .map(Future.successful)
        .getOrElse(Future.failed(new Exception("No image handlers.")))
    }
  }

  override def remove(key: Key): Future[PicResult] =
    Future.traverse(handlers)(_.remove(key)).map(_ => PicSuccess)
}
