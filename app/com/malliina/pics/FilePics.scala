package com.malliina.pics

import java.nio.file.{Files, Path, Paths}

import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.file.FileUtilities
import com.malliina.pics.FilePics.log
import com.malliina.storage.StorageLong
import play.api.Logger

import scala.concurrent.Future
import scala.util.Try

object FilePics {
  private val log = Logger(getClass)

  val PicsEnvKey = "pics.dir"
  val picsDir = sys.env.get(PicsEnvKey).map(Paths.get(_)).getOrElse(FileUtilities.tempDir.resolve("pics"))

  def apply(dir: Path, mat: Materializer): FilePics = new FilePics(dir, mat)

  def default(mat: Materializer): FilePics = apply(picsDir, mat)

  def thumbs(mat: Materializer): FilePics = apply(picsDir.resolve("thumbs"), mat)
}

class FilePics(val dir: Path, mat: Materializer) {

  Files.createDirectories(dir)

  def contains(key: Key): Boolean = Files.exists(fileAt(key))

  def get(key: Key): DataStream = {
    val file = fileAt(key)
    DataStream(
      FileIO.fromPath(file),
      Option(Files.size(file).bytes),
      ContentType.parseFile(file)
    )
  }

  def remove(key: Key): Try[Unit] = tryLogged(Files.delete(fileAt(key)))

  def put(key: Key, file: Path): Try[Unit] = tryLogged(Files.copy(file, fileAt(key)))

  def putSource(key: Key, source: Source[ByteString, Future[IOResult]]) = {
    val file = fileAt(key)
    FileIO.toPath(file).runWith(source)(mat)
      .map { res => log.info(s"Saved ${res.count} bytes to '$file' with outcome ${res.status}") }
      .recover { case t => log.error(s"Unable to save key '$key' to file", t) }
  }

  private def fileAt(key: Key) = dir resolve key.key

  def tryLogged[T](r: => T): Try[Unit] =
    Try(r).map(_ => ()).recover { case t => log.error("Pics operation failed", t) }
}

object FileCachingPics {
  def apply(cache: FilePics, origin: PicFiles): FileCachingPics =
    new FileCachingPics(cache, origin)
}

class FileCachingPics(cache: FilePics, origin: PicFiles) extends PicFiles {
  override def load(from: Int, until: Int) = origin.load(from, until)

  override def contains(key: Key): Boolean = cache.contains(key) || origin.contains(key)

  override def get(key: Key): DataStream =
    if (cache.contains(key)) {
      cache.get(key)
    } else {
      cache.putSource(key, origin.get(key).source)
      origin.get(key)
    }

  override def put(key: Key, file: Path): Try[Unit] = for {
    _ <- origin.put(key, file)
    _ <- cache.put(key, file)
  } yield ()

  override def remove(key: Key): Try[Unit] = {
    origin.remove(key)
    cache.remove(key)
  }
}
