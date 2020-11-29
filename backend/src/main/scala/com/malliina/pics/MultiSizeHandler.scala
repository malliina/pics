package com.malliina.pics

import java.nio.file.Path

import cats.data.NonEmptyList
import cats.effect.IO
import com.malliina.concurrent.Execution.cached
import com.malliina.pics.s3.AsyncS3Bucket
import com.sksamuel.scrimage.ImmutableImage

import scala.concurrent.Future

object MultiSizeHandler {
  def default(): MultiSizeHandler = new MultiSizeHandler(
    ImageHandler("small", ScrimageResizer.Small, cached("smalls", AsyncS3Bucket.Small)),
    ImageHandler("medium", ScrimageResizer.Medium, cached("mediums", AsyncS3Bucket.Medium)),
    ImageHandler("large", ScrimageResizer.Large, cached("larges", AsyncS3Bucket.Large)),
    ImageHandler("original", AsIsResizer, cached("originals", AsyncS3Bucket.Original))
  )

  def cached(name: String, origin: DataSource) =
    FileCachingPics(FilePics.named(name), origin)

  def clones(handler: ImageHandler) = new MultiSizeHandler(
    handler,
    handler,
    handler,
    handler
  )
}

class MultiSizeHandler(
  val smalls: ImageHandler,
  val mediums: ImageHandler,
  val larges: ImageHandler,
  val originals: ImageHandler
) extends ImageHandlerLike[Future] {
  val handlers = Seq(smalls, mediums, larges, originals)

  def apply(size: PicSize) = size match {
    case PicSize.Small    => smalls
    case PicSize.Medium   => mediums
    case PicSize.Large    => larges
    case PicSize.Original => originals
  }

  def handle(original: Path, key: Key): Future[Path] =
    Future(ImmutableImage.loader().fromPath(original)).flatMap { image =>
      handleImage(image, key)
    }

  override def handleImage(image: ImmutableImage, key: Key): Future[Path] = {
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

class MultiSizeHandlerIO(
  val smalls: ImageHandlerIO,
  val mediums: ImageHandlerIO,
  val larges: ImageHandlerIO,
  val originals: ImageHandlerIO
) extends ImageHandlerLike[IO] {
  val handlers = NonEmptyList.of(smalls, mediums, larges, originals)

  def apply(size: PicSize): ImageHandlerIO = size match {
    case PicSize.Small    => smalls
    case PicSize.Medium   => mediums
    case PicSize.Large    => larges
    case PicSize.Original => originals
  }

  def handle(original: Path, key: Key): IO[Path] =
    IO(ImmutableImage.loader().fromPath(original)).flatMap { image =>
      handleImage(image, key)
    }

  override def handleImage(image: ImmutableImage, key: Key): IO[Path] =
    handlers.traverse(_.handleImage(image, key)).map { paths =>
      paths.head
    }

  override def remove(key: Key): IO[PicResult] =
    handlers.traverse(_.remove(key)).map(_ => PicSuccess)
}