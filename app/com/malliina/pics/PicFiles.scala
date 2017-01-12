package com.malliina.pics

import java.nio.file.Path

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.malliina.storage.StorageSize

import scala.concurrent.Future

case class DataStream(source: Source[ByteString, Future[IOResult]],
                      contentLength: Option[StorageSize],
                      contentType: Option[ContentType])

trait PicFiles {
  def load(from: Int, until: Int): Seq[Key]

  def contains(key: Key): Boolean

  def get(key: Key): DataStream

  def find(key: Key): Option[DataStream] =
    if (contains(key)) Option(get(key))
    else None

  def put(key: Key, file: Path): Unit

  def remove(key: Key): Unit
}
