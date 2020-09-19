package com.malliina.pics

import java.io.Closeable
import java.nio.file.{Files, Path}

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.malliina.concurrent.Execution.cached
import com.malliina.storage.{StorageLong, StorageSize}

import scala.concurrent.Future

sealed trait DataResponse {
  def contentLength: Option[StorageSize]

  def contentType: Option[ContentType]

  def isImage: Boolean = contentType.exists(_.isImage)
}

case class DataFile(
  file: Path,
  contentLength: Option[StorageSize],
  contentType: Option[ContentType]
) extends DataResponse

object DataFile {
  def apply(file: Path): DataFile = DataFile(
    file,
    Option(Files.size(file).bytes),
    ContentType.parseFile(file)
  )
}

trait SourceLike {
  def contains(key: Key): Future[Boolean]

  def fut[T](t: T): Future[T] = Future.successful(t)
}

trait DataSource extends SourceLike {
  def load(from: Int, until: Int): Future[Seq[FlatMeta]]

  def get(key: Key): Future[DataFile]

  /** Removes `key`.
    *
    * @param key key to delete
    * @return success even if `key` does not exist
    */
  def remove(key: Key): Future[PicResult]

  def find(key: Key): Future[Option[DataFile]] =
    contains(key).flatMap { exists =>
      if (exists) get(key).map(Option.apply)
      else Future.successful(None)
    }

  def saveBody(key: Key, file: Path): Future[StorageSize]
}

trait MetaSource extends SourceLike {
  def load(from: Int, until: Int, user: PicOwner): Future[Seq[KeyMeta]]
  def saveMeta(key: Key, owner: PicOwner): Future[KeyMeta]
  def putMetaIfNotExists(meta: KeyMeta): Future[Int]
  def remove(key: Key, user: PicOwner): Future[Boolean]
}
