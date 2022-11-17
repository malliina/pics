package com.malliina.pics

import cats.{FlatMap, Monad}

import java.nio.file.{Files, Path}
import cats.effect.{Async, IO, Sync}
import cats.syntax.all.*
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.values.{AccessToken, Username}

sealed trait DataResponse:
  def contentLength: Option[StorageSize]
  def contentType: Option[ContentType]
  def isImage: Boolean = contentType.exists(_.isImage)

case class DataFile(
  file: Path,
  contentLength: Option[StorageSize],
  contentType: Option[ContentType]
) extends DataResponse

object DataFile:
  def apply(file: Path): DataFile = DataFile(
    file,
    Option(Files.size(file).bytes),
    ContentType.parseFile(file)
  )

trait SourceLike[F[_]]:
  def contains(key: Key): F[Boolean]

trait ImageSourceLike[F[_]] extends SourceLike[F]:

  /** Removes `key`.
    *
    * @param key
    *   key to delete
    * @return
    *   success even if `key` does not exist
    */
  def remove(key: Key): F[PicResult]
  def saveBody(key: Key, file: Path): F[StorageSize]

trait DataSourceT[F[_]: Monad] extends ImageSourceLike[F]:
  def get(key: Key): F[DataFile]
  def load(from: Int, until: Int): F[Seq[FlatMeta]]
  def find(key: Key): F[Option[DataFile]] =
    contains(key).flatMap { exists =>
      if exists then get(key).map(Option.apply)
      else Monad[F].pure(None)
    }

trait MetaSourceT[F[_]] extends SourceLike[F]:
  def meta(key: Key): F[KeyMeta]
  def load(from: Int, until: Int, user: PicOwner): F[List[KeyMeta]]
  def saveMeta(key: Key, owner: PicOwner): F[KeyMeta]
  def putMetaIfNotExists(meta: KeyMeta): F[Int]
  def remove(key: Key, user: PicOwner): F[Boolean]
  def modify(key: Key, user: PicOwner, access: Access): F[KeyMeta]

trait UserDatabase[F[_]]:
  def userByToken(token: AccessToken): F[Option[Username]]
