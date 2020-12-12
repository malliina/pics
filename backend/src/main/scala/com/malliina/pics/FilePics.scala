package com.malliina.pics

import java.nio.file.{Files, NoSuchFileException, Path, Paths}

import com.malliina.concurrent.Execution.cached
import com.malliina.pics.FilePics.log
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.util.AppLogger

import scala.concurrent.Future
import scala.jdk.CollectionConverters.IteratorHasAsScala

object FilePics {
  private val log = AppLogger(getClass)
  val tmpDir = Paths.get(sys.props("java.io.tmpdir"))

  val PicsEnvKey = "pics.dir"
  val picsDir =
    sys.env.get(PicsEnvKey).map(Paths.get(_)).getOrElse(tmpDir.resolve("pics"))

  def apply(dir: Path): FilePics = new FilePics(dir)
  def default(): FilePics = apply(picsDir)
  def thumbs(): FilePics = named("thumbs")
  def named(name: String) = apply(picsDir.resolve(name))
}

class FilePics(val dir: Path) extends DataSource {
  Files.createDirectories(dir)
  log.info(s"Using pics dir '$dir'.")

  override def load(from: Int, until: Int): Future[Seq[FlatMeta]] = Future {
    Files.list(dir).iterator().asScala.toList.slice(from, from + until).map { p =>
      FlatMeta(Key(p.getFileName.toString), Files.getLastModifiedTime(p).toInstant)
    }
  }

  override def contains(key: Key): Future[Boolean] = Future(Files.exists(fileAt(key)))

  override def get(key: Key): Future[DataFile] = Future(DataFile(fileAt(key)))

  override def remove(key: Key): Future[PicResult] =
    Future[PicResult] {
      Files.delete(fileAt(key))
      PicSuccess
    }.recoverWith {
      case _: NoSuchFileException =>
        fut(PicNotFound(key))
      case other: Exception =>
        log.error("Pics operation failed.", other)
        Future.failed(other)
    }

  def putData(key: Key, data: DataFile): Future[Path] =
    saveBody(key, data.file).map(_ => fileAt(key))

  def saveBody(key: Key, file: Path): Future[StorageSize] =
    Future {
      Files.copy(file, fileAt(key))
      Files.size(file).bytes
    }.recoverWith { case t =>
      log.error("Pics operation failed.", t)
      Future.failed(t)
    }

  private def fileAt(key: Key) = dir resolve key.key
}
