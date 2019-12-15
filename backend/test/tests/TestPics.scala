package tests

import java.nio.file.Path

import com.malliina.pics._
import com.malliina.storage.StorageSize

import scala.concurrent.Future

object TestPics extends DataSource {
  override def load(from: Int, until: Int): Future[Seq[FlatMeta]] = fut(Nil)
  override def contains(key: Key): Future[Boolean] = fut(false)
  override def get(key: Key): Future[DataFile] = {
    Future.failed(new KeyNotFound(key))
  }
  override def saveBody(key: Key, file: Path): Future[StorageSize] = fut(StorageSize.empty)
  override def remove(key: Key): Future[PicResult] = fut(PicSuccess)
}
