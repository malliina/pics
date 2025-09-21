package com.malliina.pics

import cats.effect.Sync
import cats.syntax.all.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps}
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.util.AppLogger
import fs2.io.file.{Files, Path}

import java.nio.file.NoSuchFileException
import java.time.Instant

object FilePicsIO:
  private val log = AppLogger(getClass)
  val tmpDir = Path(sys.props("java.io.tmpdir"))

  private val PicsEnvKey = "pics.dir"
  private val picsDir =
    sys.env.get(PicsEnvKey).map(Path(_)).getOrElse(tmpDir.resolve("pics"))

  def default[F[_]: {Sync, Files}](): F[FilePicsIO[F]] = directory(picsDir)
  def thumbs[F[_]: {Sync, Files}](): F[FilePicsIO[F]] = named("thumbs")
  def named[F[_]: {Sync, Files}](name: String): F[FilePicsIO[F]] = directory(picsDir.resolve(name))

  private def directory[F[_]: {Sync, Files}](dir: Path) =
    Files[F]
      .createDirectories(dir)
      .map: _ =>
        new FilePicsIO[F](dir)

class FilePicsIO[F[_]: {Sync, Files}](val dir: Path) extends DataSourceT[F]:
  val S = Sync[F]
  val F = Files[F]
  import FilePicsIO.log
  log.info(s"Using pics dir '$dir'.")

  override def load(from: Int, until: Int): F[List[FlatMeta]] =
    F.list(dir)
      .drop(from)
      .take(from + until)
      .flatMap: p =>
        fs2.Stream
          .eval(F.getLastModifiedTime(p))
          .flatMap: lastModified =>
            val lastMod = Instant.ofEpochMilli(lastModified.toMillis)
            Key
              .build(p.fileName.toString)
              .map: key =>
                fs2.Stream(FlatMeta(key, lastMod))
              .getOrElse:
                fs2.Stream.empty
      .compile
      .toList

  override def contains(key: Key): F[Boolean] = F.exists(fileAt(key))
  override def get(key: Key): F[DataFile] = DataFile(fileAt(key))
  override def remove(key: Key): F[PicResult] =
    F.delete(fileAt(key))
      .map(_ => PicSuccess)
      .handleErrorWith:
        case _: NoSuchFileException =>
          S.pure(PicNotFound(key))
        case other: Exception =>
          log.error("Pics operation failed.", other)
          S.raiseError(other)

  def putData(key: Key, data: DataFile): F[Path] =
    saveBody(key, data.file).map(_ => fileAt(key))

  def saveBody(key: Key, file: Path): F[StorageSize] =
    val op = for
      _ <- F.copy(file, fileAt(key))
      size <- F.size(file)
    yield size.bytes
    op
      .handleErrorWith: t =>
        log.error("Pics operation failed.", t)
        S.raiseError(t)

  private def fileAt(key: Key) = dir.resolve(key.key)
