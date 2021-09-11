package com.malliina.pics

import java.nio.file.Path

import cats.data.NonEmptyList
import cats.effect.IO
import com.malliina.pics.s3.S3Source
import com.sksamuel.scrimage.ImmutableImage

object MultiSizeHandlerIO:
  def default(): MultiSizeHandlerIO =
    apply(S3Source.Small, S3Source.Medium, S3Source.Large, S3Source.Original)

  def empty(): MultiSizeHandlerIO = apply(
    FilePicsIO.named("small"),
    FilePicsIO.named("medium"),
    FilePicsIO.named("large"),
    FilePicsIO.named("original")
  )

  def apply(
    small: DataSourceT[IO],
    medium: DataSourceT[IO],
    large: DataSourceT[IO],
    original: DataSourceT[IO]
  ) =
    new MultiSizeHandlerIO(
      ImageHandlerIO("small", ScrimageResizerIO.Small, cached("smalls", small)),
      ImageHandlerIO("medium", ScrimageResizerIO.Medium, cached("mediums", medium)),
      ImageHandlerIO("large", ScrimageResizerIO.Large, cached("larges", large)),
      ImageHandlerIO("original", AsIsResizerIO, cached("originals", original))
    )

  def cached(name: String, origin: DataSourceT[IO]) =
    FileCachingPicsIO(FilePicsIO.named(name), origin)

class MultiSizeHandlerIO(
  val smalls: ImageHandlerIO,
  val mediums: ImageHandlerIO,
  val larges: ImageHandlerIO,
  val originals: ImageHandlerIO
) extends ImageHandlerLike[IO]:
  val handlers = NonEmptyList.of(smalls, mediums, larges, originals)

  def apply(size: PicSize): ImageHandlerIO = size match
    case PicSize.Small    => smalls
    case PicSize.Medium   => mediums
    case PicSize.Large    => larges
    case PicSize.Original => originals

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
