package tests

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.file.Path

import akka.stream.scaladsl.StreamConverters
import com.malliina.pics.{DataStream, Key, PicFiles}

object TestPics extends PicFiles {
  override def load(from: Int, until: Int): Seq[Key] = Nil

  override def contains(key: Key): Boolean = false

  override def get(key: Key): DataStream = {
    val inStream: InputStream = new ByteArrayInputStream(Array.empty)
    val source = StreamConverters.fromInputStream(() => inStream)
    DataStream(source, None, None)
  }

  override def put(key: Key, file: Path): Unit = ()

  override def remove(key: Key): Unit = ()
}
