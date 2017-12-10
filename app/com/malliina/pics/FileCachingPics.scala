package com.malliina.pics

import java.nio.file.Path

import com.malliina.concurrent.ExecutionContexts.cached

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

  override def get(key: Key): Future[DataResponse] =
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

  override def saveBody(key: Key, file: Path): Future[Unit] =
    for {
      _ <- origin.saveBody(key, file)
      _ <- cache.saveBody(key, file)
    } yield ()

  override def remove(key: Key): Future[Unit] =
    for {
      _ <- origin.remove(key)
      _ <- cache.remove(key)
    } yield ()
}
