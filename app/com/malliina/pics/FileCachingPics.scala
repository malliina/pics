package com.malliina.pics

import java.nio.file.Path

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.play.models.Username

import scala.concurrent.Future

object FileCachingPics {
  def apply(cache: FilePics, origin: PicFiles): FileCachingPics =
    new FileCachingPics(cache, origin)
}

class FileCachingPics(cache: FilePics, origin: PicFiles) extends PicFiles {
  override def load(from: Int, until: Int, user: Username) = origin.load(from, until, user)

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

  override def remove(key: Key, user: Username): Future[Unit] =
    for {
      _ <- origin.remove(key, user)
      _ <- Future.fromTry(cache.remove(key))
    } yield ()

}

object FlatFileCachingPics {
  def apply(cache: FilePics, origin: FlatFiles): FlatFileCachingPics =
    new FlatFileCachingPics(cache, origin)
}

class FlatFileCachingPics(cache: FilePics, origin: FlatFiles) extends FlatFiles {
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
