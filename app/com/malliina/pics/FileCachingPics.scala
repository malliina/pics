package com.malliina.pics

import java.nio.file.Path

import scala.util.Try

object FileCachingPics {
  def apply(cache: FilePics, origin: PicFiles): FileCachingPics =
    new FileCachingPics(cache, origin)
}

class FileCachingPics(cache: FilePics, origin: PicFiles) extends PicFiles {
  override def load(from: Int, until: Int) = origin.load(from, until)

  override def contains(key: Key): Boolean = cache.contains(key) || origin.contains(key)

  override def get(key: Key): DataResponse =
    if (cache.contains(key)) {
      cache.get(key)
    } else {
      origin.get(key)
      cache.putData(key, origin.get(key))
      origin.get(key)
    }

  override def put(key: Key, file: Path): Try[Unit] = for {
    _ <- origin.put(key, file)
    _ <- cache.put(key, file)
  } yield ()

  override def remove(key: Key): Try[Unit] = {
    origin.remove(key)
    cache.remove(key)
  }
}
