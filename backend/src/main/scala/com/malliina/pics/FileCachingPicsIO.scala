package com.malliina.pics

import java.nio.file.Path

import cats.effect.IO
import com.malliina.storage.StorageSize

class FileCachingPicsIO(cache: FilePicsIO, origin: DataSourceT[IO]) extends DataSourceIO:
  override def load(from: Int, until: Int) = origin.load(from, until)

  override def contains(key: Key): IO[Boolean] =
    cache.contains(key).flatMap { isCached =>
      if isCached then IO.pure(true)
      else origin.contains(key)
    }

  override def get(key: Key): IO[DataFile] =
    cache.contains(key).flatMap { isCached =>
      if isCached then cache.get(key)
      else
        origin.get(key).flatMap { r =>
          cache.putData(key, r).map { file =>
            DataFile(file)
          }
        }
    }

  override def saveBody(key: Key, file: Path): IO[StorageSize] =
    for
      size <- origin.saveBody(key, file)
      _ <- cache.saveBody(key, file)
    yield size

  override def remove(key: Key): IO[PicResult] =
    for
      result <- origin.remove(key)
      _ <- cache.remove(key)
    yield result
