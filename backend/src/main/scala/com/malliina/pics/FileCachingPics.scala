package com.malliina.pics

import cats.Monad
import cats.effect.Sync
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.storage.StorageSize
import fs2.io.file.{Files, Path}

class FileCachingPics[F[_]: {Sync, Files}](cache: FilePicsIO[F], origin: DataSourceT[F])
  extends DataSourceT[F]:
  override def load(from: Int, until: Int): F[List[FlatMeta]] = origin.load(from, until)

  override def contains(key: Key): F[Boolean] =
    cache
      .contains(key)
      .flatMap: isCached =>
        if isCached then Monad[F].pure(true)
        else origin.contains(key)

  override def get(key: Key): F[DataFile] =
    cache
      .contains(key)
      .flatMap: isCached =>
        if isCached then cache.get(key)
        else
          origin
            .get(key)
            .flatMap: r =>
              for
                file <- cache.putData(key, r)
                dataFile <- DataFile(file)
              yield dataFile

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
