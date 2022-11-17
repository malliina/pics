package com.malliina.pics

import cats.Monad

import java.nio.file.{Files, NoSuchFileException, Path, Paths}
import cats.effect.{IO, Sync}
import cats.syntax.all.*
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.util.AppLogger

import scala.jdk.CollectionConverters.IteratorHasAsScala

object FilePicsIO:
  private val log = AppLogger(getClass)
  val tmpDir = Paths.get(sys.props("java.io.tmpdir"))

  val PicsEnvKey = "pics.dir"
  val picsDir =
    sys.env.get(PicsEnvKey).map(Paths.get(_)).getOrElse(tmpDir.resolve("pics"))

  def default[F[_]: Sync](): FilePicsIO[F] = new FilePicsIO[F](picsDir)
  def thumbs[F[_]: Sync](): FilePicsIO[F] = named("thumbs")
  def named[F[_]: Sync](name: String): FilePicsIO[F] = new FilePicsIO[F](picsDir.resolve(name))

class FilePicsIO[F[_]: Sync](val dir: Path) extends DataSourceT[F]:
  import FilePicsIO.log
  Files.createDirectories(dir)
  log.info(s"Using pics dir '$dir'.")

  override def load(from: Int, until: Int): F[Seq[FlatMeta]] = Sync[F].blocking {
    Files.list(dir).iterator().asScala.toList.slice(from, from + until).map { p =>
      FlatMeta(Key(p.getFileName.toString), Files.getLastModifiedTime(p).toInstant)
    }
  }

  override def contains(key: Key): F[Boolean] = Sync[F].blocking(Files.exists(fileAt(key)))
  override def get(key: Key): F[DataFile] = Sync[F].pure(DataFile(fileAt(key)))
  override def remove(key: Key): F[PicResult] =
    Sync[F]
      .blocking[PicResult] {
        Files.delete(fileAt(key))
        PicSuccess
      }
      .handleErrorWith {
        case _: NoSuchFileException =>
          Monad[F].pure(PicNotFound(key))
        case other: Exception =>
          log.error("Pics operation failed.", other)
          Sync[F].raiseError(other)
      }

  def putData(key: Key, data: DataFile): F[Path] =
    saveBody(key, data.file).map(_ => fileAt(key))

  def saveBody(key: Key, file: Path): F[StorageSize] =
    Sync[F].delay {
      Files.copy(file, fileAt(key))
      Files.size(file).bytes
    }.handleErrorWith { t =>
      log.error("Pics operation failed.", t)
      Sync[F].raiseError(t)
    }

  private def fileAt(key: Key) = dir resolve key.key
