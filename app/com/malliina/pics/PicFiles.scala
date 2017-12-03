package com.malliina.pics

import java.nio.file.{Files, Path}

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.play.models.Username
import com.malliina.storage.{StorageLong, StorageSize}

import scala.concurrent.Future

sealed trait DataResponse {
  def contentLength: Option[StorageSize]

  def contentType: Option[ContentType]

  def isImage: Boolean = contentType.exists(_.isImage)
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

trait PicFiles extends MetaSource {
  def get(key: Key): Future[DataResponse]

  def find(key: Key): Future[Option[DataResponse]] =
    contains(key).flatMap { exists =>
      if (exists) get(key).map(Option.apply)
      else Future.successful(None)
    }
}

trait MetaSource {
  def load(from: Int, until: Int, user: Username): Future[Seq[KeyMeta]]

  def contains(key: Key): Future[Boolean]

  def put(key: Key, file: Path): Future[Unit]

  def remove(key: Key, user: Username): Future[Unit]

  def fut[T](t: T): Future[T] = Future.successful(t)
}

trait FlatFiles extends FlatMetaSource {
  def get(key: Key): Future[DataResponse]

  def find(key: Key): Future[Option[DataResponse]] =
    contains(key).flatMap { exists =>
      if (exists) get(key).map(Option.apply)
      else Future.successful(None)
    }
}

trait FlatMetaSource {
  def load(from: Int, until: Int): Future[Seq[FlatMeta]]

  def contains(key: Key): Future[Boolean]

  def put(key: Key, file: Path): Future[Unit]

  def remove(key: Key): Future[Unit]

  def fut[T](t: T): Future[T] = Future.successful(t)
}