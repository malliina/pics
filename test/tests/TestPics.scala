package tests

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.file.Path

import akka.stream.scaladsl.StreamConverters
import com.malliina.pics.{DataStream, Key, PicFiles}

import scala.concurrent.Future
import scala.util.Try

object TestPics extends PicFiles {
  override def load(from: Int, until: Int): Seq[Key] = Nil

  override def contains(key: Key): Boolean = false

  override def get(key: Key): Future[DataStream] = {
    val inStream: InputStream = new ByteArrayInputStream(Array.empty)
    val source = StreamConverters.fromInputStream(() => inStream)
    Future.successful(DataStream(source, None, None))
  }

  override def put(key: Key, file: Path): Try[Unit] = Try(())

  override def remove(key: Key): Try[Unit] = Try(())
}
