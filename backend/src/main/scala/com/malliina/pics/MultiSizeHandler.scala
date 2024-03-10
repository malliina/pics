package com.malliina.pics

import cats.effect.{Async, Resource, Sync}
import cats.implicits.*
import cats.data.NonEmptyList
import com.malliina.pics.s3.S3Source
import com.sksamuel.scrimage.ImmutableImage
import fs2.io.file.{Files, Path}

object MultiSizeHandler:
  def default[F[_]: Async]: Resource[F, MultiSizeHandler[F]] = for
    sm <- S3Source.Small[F]
    md <- S3Source.Medium[F]
    lg <- S3Source.Large[F]
    orig <- S3Source.Original[F]
    combo <- Resource.eval(combined(sm, md, lg, orig))
  yield combo

  def empty[F[_]: Sync: Files](): F[MultiSizeHandler[F]] =
    for
      sm <- FilePicsIO.named("small")
      m <- FilePicsIO.named("medium")
      l <- FilePicsIO.named("large")
      o <- FilePicsIO.named("original")
      combo <- combined(sm, m, l, o)
    yield combo

  def combined[F[_]: Sync: Files](
    small: DataSourceT[F],
    medium: DataSourceT[F],
    large: DataSourceT[F],
    original: DataSourceT[F]
  ): F[MultiSizeHandler[F]] =
    for
      cs <- cached("smalls", small)
      cm <- cached("mediums", medium)
      cl <- cached("larges", large)
      co <- cached("originals", original)
    yield new MultiSizeHandler(
      ImageHandler("small", ScrimageResizer.Small, cs),
      ImageHandler("medium", ScrimageResizer.Medium, cm),
      ImageHandler("large", ScrimageResizer.Large, cl),
      ImageHandler("original", AsIsResizer[F], co)
    )

  def cached[F[_]: Sync: Files](name: String, origin: DataSourceT[F]) =
    FilePicsIO
      .named(name)
      .map: fp =>
        FileCachingPics[F](fp, origin)

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
    Sync[F]
      .blocking(ImmutableImage.loader().fromPath(original.toNioPath))
      .flatMap: image =>
        handleImage(image, key)

  override def handleImage(image: ImmutableImage, key: Key): F[Path] =
    handlers
      .traverse(_.handleImage(image, key))
      .map: paths =>
        paths.head

  override def remove(key: Key): F[PicResult] =
    handlers.traverse(_.remove(key)).map(_ => PicSuccess)
