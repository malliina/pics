package com.malliina.pics

import akka.stream.Materializer
import com.malliina.concurrent.ExecutionContexts.cached

import scala.concurrent.Future

object PicSources {
  def clones(dataSource: DataSource) = new PicSources(dataSource, dataSource, dataSource, dataSource)

  def prod(mat: Materializer) = {
    def cached(name: String, origin: BucketFiles) =
      FileCachingPics(FilePics.named(name, mat), origin)

    new PicSources(
      cached("smalls", BucketFiles.Small),
      cached("mediums", BucketFiles.Medium),
      cached("larges", BucketFiles.Large),
      cached("originals", BucketFiles.Original)
    )
  }
}

class PicSources(val smalls: DataSource,
                 val mediums: DataSource,
                 val larges: DataSource,
                 val originals: DataSource) {
  def get(key: Key, size: PicSize): Future[DataResponse] =
    withSize(size)(_.get(key))

  def find(key: Key, size: PicSize): Future[Option[DataResponse]] =
    withSize(size)(_.find(key))

  def withSize[T](size: PicSize)(f: DataSource => T) = size match {
    case Small => f(smalls)
    case Medium => f(mediums)
    case Large => f(larges)
    case Original => f(originals)
  }

  def save(key: Key, resized: PicBundle): Future[Unit] =
    for {
      _ <- smalls.saveBody(key, resized.small)
      _ <- mediums.saveBody(key, resized.medium)
      _ <- larges.saveBody(key, resized.large)
      _ <- originals.saveBody(key, resized.original)
    } yield ()

  def remove(key: Key): Future[Unit] = {
    for {
      _ <- originals.remove(key)
      _ <- smalls.remove(key)
      _ <- mediums.remove(key)
      _ <- larges.remove(key)
    } yield ()
  }
}
