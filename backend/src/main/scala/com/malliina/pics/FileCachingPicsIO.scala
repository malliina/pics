package com.malliina.pics

import cats.Monad

import java.nio.file.Path
import cats.effect.IO
import cats.syntax.all.*
import com.malliina.storage.StorageSize

class FileCachingPics[F[_]: Monad](cache: FilePicsIO[F], origin: DataSourceT[F])
  extends DataSourceT[F]:
  override def load(from: Int, until: Int): F[Seq[FlatMeta]] = origin.load(from, until)

  override def contains(key: Key): F[Boolean] =
    cache.contains(key).flatMap { isCached =>
      if isCached then Monad[F].pure(true)
      else origin.contains(key)
    }

  override def get(key: Key): F[DataFile] =
    cache.contains(key).flatMap { isCached =>
      if isCached then cache.get(key)
      else
        origin.get(key).flatMap { r =>
          cache.putData(key, r).map { file =>
            DataFile(file)
          }
        }
    }

  override def saveBody(key: Key, file: Path): F[StorageSize] =
    for
      size <- origin.saveBody(key, file)
      _ <- cache.saveBody(key, file)
    yield size

  override def remove(key: Key): F[PicResult] =
    for
      result <- origin.remove(key)
      _ <- cache.remove(key)
    yield result
