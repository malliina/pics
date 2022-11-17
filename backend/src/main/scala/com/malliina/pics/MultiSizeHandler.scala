package com.malliina.pics

import java.nio.file.Path
import cats.effect.{Async, IO, Resource, Sync}
import cats.implicits.*
import cats.syntax.*
import cats.data.NonEmptyList
import com.malliina.pics.s3.S3Source
import com.sksamuel.scrimage.ImmutableImage

object MultiSizeHandler:
  def default[F[_]: Async]: Resource[F, MultiSizeHandler[F]] = for
    sm <- S3Source.Small[F]
    md <- S3Source.Medium[F]
    lg <- S3Source.Large[F]
    orig <- S3Source.Original[F]
  yield combined(sm, md, lg, orig)

  def empty[F[_]: Sync](): MultiSizeHandler[F] = combined(
    FilePicsIO.named("small"),
    FilePicsIO.named("medium"),
    FilePicsIO.named("large"),
    FilePicsIO.named("original")
  )

  def combined[F[_]: Sync](
    small: DataSourceT[F],
    medium: DataSourceT[F],
    large: DataSourceT[F],
    original: DataSourceT[F]
  ) =
    new MultiSizeHandler(
      ImageHandler("small", ScrimageResizer.Small, cached("smalls", small)),
      ImageHandler("medium", ScrimageResizer.Medium, cached("mediums", medium)),
      ImageHandler("large", ScrimageResizer.Large, cached("larges", large)),
      ImageHandler("original", AsIsResizer[F], cached("originals", original))
    )

  def cached[F[_]: Sync](name: String, origin: DataSourceT[F]) =
    FileCachingPics[F](FilePicsIO.named(name), origin)

class MultiSizeHandler[F[_]: Sync](
  val smalls: ImageHandler[F],
  val mediums: ImageHandler[F],
  val larges: ImageHandler[F],
  val originals: ImageHandler[F]
) extends ImageHandlerLike[F]:
  val handlers = NonEmptyList.of(smalls, mediums, larges, originals)

  def apply(size: PicSize): ImageHandler[F] = size match
    case PicSize.Small    => smalls
    case PicSize.Medium   => mediums
    case PicSize.Large    => larges
    case PicSize.Original => originals

  def handle(original: Path, key: Key): F[Path] =
    Sync[F].blocking(ImmutableImage.loader().fromPath(original)).flatMap { image =>
      handleImage(image, key)
    }

  override def handleImage(image: ImmutableImage, key: Key): F[Path] =
    handlers.traverse(_.handleImage(image, key)).map { paths =>
      paths.head
    }

  override def remove(key: Key): F[PicResult] =
    handlers.traverse(_.remove(key)).map(_ => PicSuccess)
