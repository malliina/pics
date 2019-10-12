package com.malliina.pics

import java.nio.file.{Files, NoSuchFileException, Path, Paths}

import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import com.malliina.concurrent.Execution.cached
import com.malliina.pics.FilePics.log
import com.malliina.storage.{StorageLong, StorageSize}
import play.api.Logger

import scala.concurrent.Future
import scala.jdk.CollectionConverters.IteratorHasAsScala

object FilePics {
  private val log = Logger(getClass)
  val tmpDir = Paths.get(sys.props("java.io.tmpdir"))

  val PicsEnvKey = "pics.dir"
  val picsDir =
    sys.env.get(PicsEnvKey).map(Paths.get(_)).getOrElse(tmpDir.resolve("pics"))

  def apply(dir: Path, mat: Materializer): FilePics = new FilePics(dir, mat)

  def default(mat: Materializer): FilePics = apply(picsDir, mat)

  def thumbs(mat: Materializer): FilePics = named("thumbs", mat)

  def named(name: String, mat: Materializer) = apply(picsDir.resolve(name), mat)
}

class FilePics(val dir: Path, mat: Materializer) extends DataSource {
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
    }.recoverWith {
      case t =>
        log.error("Pics operation failed.", t)
        Future.failed(t)
    }

  def putSource(key: Key, source: Source[ByteString, Future[IOResult]]): Future[Path] = {
    val file = fileAt(key)
    log.info(s"Saving '$key' to '$file'...")
    FileIO
      .toPath(file)
      .runWith(source)(mat)
      .map { res =>
        val outcome = if (res.status.isSuccess) "successfully" else "erroneously"
        log.info(s"Saved '$key' of ${res.count} bytes to '$file' $outcome.")
        file
      }
      .recoverWith {
        case t =>
          log.error(s"Unable to save key '$key' to file.", t)
          Future.failed(new Exception(s"Unable to save '$key'."))
      }
  }

  private def fileAt(key: Key) = dir resolve key.key
}
