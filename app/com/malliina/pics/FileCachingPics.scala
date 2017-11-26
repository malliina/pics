package com.malliina.pics

import java.nio.file.Path

import com.malliina.concurrent.ExecutionContexts.cached

import scala.concurrent.Future
import scala.util.Try

object FileCachingPics {
  def apply(cache: FilePics, origin: PicFiles): FileCachingPics =
    new FileCachingPics(cache, origin)
}

class FileCachingPics(cache: FilePics, origin: PicFiles) extends PicFiles {
  override def load(from: Int, until: Int) = origin.load(from, until)

  override def contains(key: Key): Future[Boolean] = {
    val isCached = cache.contains(key)
    if (isCached) fut(true)
    else origin.contains(key)
  }

  override def get(key: Key): Future[DataResponse] =
    if (cache.contains(key)) {
      cache.get(key)
    } else {
      origin.get(key).flatMap { r =>
        cache.putData(key, r).map { file =>
          DataFile(file)
        }
      }
    }

  override def put(key: Key, file: Path): Future[Unit] =
    for {
      _ <- origin.put(key, file)
      _ <- Future.fromTry(cache.put(key, file))
    } yield ()

  override def remove(key: Key): Future[Unit] =
    for {
      _ <- origin.remove(key)
      _ <- Future.fromTry(cache.remove(key))
    } yield ()

}
