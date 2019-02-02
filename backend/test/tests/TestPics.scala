package tests

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.file.Path

import akka.stream.scaladsl.StreamConverters
import com.malliina.pics._

import scala.concurrent.Future

object TestPics extends DataSource {
  override def load(from: Int, until: Int): Future[Seq[FlatMeta]] = fut(Nil)

  override def contains(key: Key): Future[Boolean] = fut(false)

  override def get(key: Key): Future[DataStream] = {
    val inStream: InputStream = new ByteArrayInputStream(Array.empty)
    val source = StreamConverters.fromInputStream(() => inStream)
    fut(DataStream(source, None, None))
  }

  override def saveBody(key: Key, file: Path): Future[Unit] = fut(())

  override def remove(key: Key): Future[PicResult] = fut(PicSuccess)
}
