package com.malliina.pics

import java.nio.file.{Files, NoSuchFileException, Path, Paths}

import cats.effect.IO
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.util.AppLogger

import scala.jdk.CollectionConverters.IteratorHasAsScala

object FilePicsIO:
  private val log = AppLogger(getClass)
  val tmpDir = Paths.get(sys.props("java.io.tmpdir"))

  val PicsEnvKey = "pics.dir"
  val picsDir =
    sys.env.get(PicsEnvKey).map(Paths.get(_)).getOrElse(tmpDir.resolve("pics"))

  def apply(dir: Path): FilePicsIO = new FilePicsIO(dir)
  def default(): FilePicsIO = apply(picsDir)
  def thumbs(): FilePicsIO = named("thumbs")
  def named(name: String) = apply(picsDir.resolve(name))

class FilePicsIO(val dir: Path) extends DataSourceIO:
  import FilePicsIO.log
  Files.createDirectories(dir)
  log.info(s"Using pics dir '$dir'.")

  override def load(from: Int, until: Int): IO[Seq[FlatMeta]] = IO {
    Files.list(dir).iterator().asScala.toList.slice(from, from + until).map { p =>
      FlatMeta(Key(p.getFileName.toString), Files.getLastModifiedTime(p).toInstant)
    }
  }

  override def contains(key: Key): IO[Boolean] = IO(Files.exists(fileAt(key)))

  override def get(key: Key): IO[DataFile] = IO(DataFile(fileAt(key)))

  override def remove(key: Key): IO[PicResult] =
    IO[PicResult] {
      Files.delete(fileAt(key))
      PicSuccess
    }.handleErrorWith {
      case _: NoSuchFileException =>
        IO.pure(PicNotFound(key))
      case other: Exception =>
        log.error("Pics operation failed.", other)
        IO.raiseError(other)
    }

  def putData(key: Key, data: DataFile): IO[Path] =
    saveBody(key, data.file).map(_ => fileAt(key))

  def saveBody(key: Key, file: Path): IO[StorageSize] =
    IO {
      Files.copy(file, fileAt(key))
      Files.size(file).bytes
    }.handleErrorWith { t =>
      log.error("Pics operation failed.", t)
      IO.raiseError(t)
    }

  private def fileAt(key: Key) = dir resolve key.key
