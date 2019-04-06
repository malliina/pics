package com.malliina.pics

import java.nio.file.Path

import com.malliina.concurrent.Execution.cached
import com.malliina.storage.StorageSize

import scala.concurrent.Future

object FileCachingPics {
  def apply(cache: FilePics, origin: DataSource): FileCachingPics =
    new FileCachingPics(cache, origin)
}

class FileCachingPics(cache: FilePics, origin: DataSource) extends DataSource {
  override def load(from: Int, until: Int) = origin.load(from, until)

  override def contains(key: Key): Future[Boolean] = {
    cache.contains(key).flatMap { isCached =>
      if (isCached) fut(true)
      else origin.contains(key)
    }
  }

  override def get(key: Key): Future[DataFile] =
    cache.contains(key).flatMap { isCached =>
      if (isCached) {
        cache.get(key)
      } else {
        origin.get(key).flatMap { r =>
          cache.putData(key, r).map { file =>
            DataFile(file)
          }
        }
      }
    }

  override def saveBody(key: Key, file: Path): Future[StorageSize] =
    for {
      size <- origin.saveBody(key, file)
      _ <- cache.saveBody(key, file)
    } yield size

  override def remove(key: Key): Future[PicResult] =
    for {
      result <- origin.remove(key)
      _ <- cache.remove(key)
    } yield result
}
