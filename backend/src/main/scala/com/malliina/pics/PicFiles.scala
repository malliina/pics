package com.malliina.pics

import java.nio.file.{Files, Path}
import cats.effect.IO
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.values.{AccessToken, Username}

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
}

trait ImageSourceLike[F[_]] extends SourceLike[F] {

  /** Removes `key`.
    *
    * @param key key to delete
    * @return success even if `key` does not exist
    */
  def remove(key: Key): F[PicResult]
  def saveBody(key: Key, file: Path): F[StorageSize]
}

trait DataSourceT[F[_]] extends ImageSourceLike[F] {
  def get(key: Key): F[DataFile]
  def load(from: Int, until: Int): F[Seq[FlatMeta]]
}

trait DataSourceIO extends DataSourceT[IO] {
  def find(key: Key): IO[Option[DataFile]] =
    contains(key).flatMap { exists =>
      if (exists) get(key).map(Option.apply)
      else IO.pure(None)
    }
}

trait MetaSourceT[F[_]] extends SourceLike[F] {
  def load(from: Int, until: Int, user: PicOwner): F[List[KeyMeta]]
  def saveMeta(key: Key, owner: PicOwner): F[KeyMeta]
  def putMetaIfNotExists(meta: KeyMeta): F[Int]
  def remove(key: Key, user: PicOwner): F[Boolean]
}

trait UserDatabase[F[_]] {
  def userByToken(token: AccessToken): F[Option[Username]]
}
