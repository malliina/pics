package com.malliina.pics

import cats.Monad
import cats.effect.Sync
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.storage.{StorageLong, StorageSize}
import com.malliina.values.{AccessToken, NonNeg, Username}
import fs2.io.file.{Files, Path}

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
  def apply[F[_]: {Sync, Files}](file: Path): F[DataFile] =
    val F = Files[F]
    F.size(file)
      .map: bytes =>
        DataFile(
          file,
          Option(bytes.bytes),
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
  def load(from: Int, until: Int): F[List[FlatMeta]]
  def find(key: Key): F[Option[DataFile]] =
    contains(key).flatMap: exists =>
      if exists then get(key).map(Option.apply)
      else Monad[F].pure(None)

trait MetaSourceT[F[_]] extends SourceLike[F]:
  def meta(key: Key): F[KeyMeta]
  def load(from: NonNeg, until: NonNeg, user: PicOwner): F[List[KeyMeta]]
  def saveMeta(key: Key, owner: PicOwner): F[KeyMeta]
  def putMetaIfNotExists(meta: KeyMeta): F[Int]
  def remove(key: Key, user: PicOwner): F[Boolean]
  def modify(key: Key, user: PicOwner, access: Access): F[KeyMeta]

trait UserDatabase[F[_]]:
  def userByToken(token: AccessToken): F[Option[Username]]
