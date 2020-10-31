package com.malliina.pics

import java.nio.file.{Files, Path}

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

trait SourceLike[F[_]] {
  def contains(key: Key): F[Boolean]
  def fut[T](t: T): Future[T] = Future.successful(t)
}

trait DataSource extends SourceLike[Future] {
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

trait MetaSourceT[F[_]] extends SourceLike[F] {
  def load(from: Int, until: Int, user: PicOwner): F[List[KeyMeta]]
  def saveMeta(key: Key, owner: PicOwner): F[KeyMeta]
  def putMetaIfNotExists(meta: KeyMeta): F[Int]
  def remove(key: Key, user: PicOwner): F[Boolean]
}

trait MetaSource extends MetaSourceT[Future]
