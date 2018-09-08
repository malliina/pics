package com.malliina.pics

import java.nio.file.{Files, NoSuchFileException, Path, Paths}

import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import com.malliina.concurrent.Execution.cached
import com.malliina.pics.FilePics.log
import com.malliina.pics.db.PicsDatabase
import play.api.Logger

import scala.concurrent.Future
import scala.util.Try

object FilePics {
  private val log = Logger(getClass)

  val PicsEnvKey = "pics.dir"
  val picsDir = sys.env.get(PicsEnvKey).map(Paths.get(_)).getOrElse(PicsDatabase.tmpDir.resolve("pics"))

  def apply(dir: Path, mat: Materializer): FilePics = new FilePics(dir, mat)

  def default(mat: Materializer): FilePics = apply(picsDir, mat)

  def thumbs(mat: Materializer): FilePics = named("thumbs", mat)

  def named(name: String, mat: Materializer) = apply(picsDir.resolve(name), mat)
}

class FilePics(val dir: Path, mat: Materializer) extends DataSource {
  Files.createDirectories(dir)

  import scala.collection.JavaConverters._

  override def load(from: Int, until: Int): Future[Seq[FlatMeta]] = fut {
    Files.list(dir).iterator().asScala.toList.slice(from, from + until).map { p =>
      FlatMeta(Key(p.getFileName.toString), Files.getLastModifiedTime(p).toInstant)
    }
  }

  override def contains(key: Key): Future[Boolean] = fut(Files.exists(fileAt(key)))

  override def get(key: Key): Future[DataResponse] = fut(DataFile(fileAt(key)))

  override def remove(key: Key): Future[PicResult] =
    try {
      Files.delete(fileAt(key))
      fut(PicSuccess)
    } catch {
      case _: NoSuchFileException =>
        fut(PicNotFound(key))
      case other: Exception =>
        log.error("Pics operation failed", other)
        Future.failed(other)
    }

  def putData(key: Key, data: DataResponse): Future[Path] = data match {
    case DataStream(source, _, _) => putSource(key, source)
    case DataFile(file, _, _) => saveBody(key, file).map(_ => fileAt(key))
  }

  def saveBody(key: Key, file: Path): Future[Unit] =
    Future.fromTry(tryLogged(Files.copy(file, fileAt(key))))

  def putSource(key: Key, source: Source[ByteString, Future[IOResult]]): Future[Path] = {
    val file = fileAt(key)
    FileIO.toPath(file).runWith(source)(mat).map { res =>
      log.info(s"Saved ${res.count} bytes to '$file' with outcome ${res.status}")
      file
    }.recoverWith { case t =>
      log.error(s"Unable to save key '$key' to file", t)
      Future.failed(new Exception(s"Unable to save '$key'."))
    }
  }

  private def fileAt(key: Key) = dir resolve key.key

  def tryLogged[T](r: => T): Try[Unit] =
    Try(r).map(_ => ()).recover { case t => log.error("Pics operation failed", t) }
}
