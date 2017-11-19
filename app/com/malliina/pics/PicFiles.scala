package com.malliina.pics

import java.nio.file.{Files, Path}

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.storage.{StorageLong, StorageSize}

import scala.concurrent.Future
import scala.util.Try

sealed trait DataResponse {
  def contentLength: Option[StorageSize]

  def contentType: Option[ContentType]

  def isImage: Boolean = contentType.exists(_.contentType startsWith "image")
}

case class DataFile(file: Path,
                    contentLength: Option[StorageSize],
                    contentType: Option[ContentType]) extends DataResponse

object DataFile {
  def apply(file: Path): DataFile = DataFile(
    file,
    Option(Files.size(file).bytes),
    ContentType.parseFile(file)
  )
}

case class DataStream(source: Source[ByteString, Future[IOResult]],
                      contentLength: Option[StorageSize],
                      contentType: Option[ContentType]) extends DataResponse

trait PicFiles {
  def load(from: Int, until: Int): Seq[Key]

  def contains(key: Key): Boolean

  def get(key: Key): Future[DataResponse]

  def find(key: Key): Future[Option[DataResponse]] =
    if (contains(key)) get(key).map(Option.apply)
    else Future.successful(None)

  def put(key: Key, file: Path): Try[Unit]

  def remove(key: Key): Try[Unit]
}
