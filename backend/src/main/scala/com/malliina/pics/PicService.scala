package com.malliina.pics

import java.nio.file.{Files, Path}

import cats.effect._
import com.malliina.concurrent.Execution.cached
import com.malliina.pics.PicService.log
import org.apache.commons.io.FilenameUtils
import play.api.Logger

import scala.concurrent.Future

trait PicServiceT[F[_]] {
  def save(tempFile: Path, by: BaseRequest, preferredName: Option[String]): F[KeyMeta]
}

class PicServiceIO(db: MetaSourceT[IO], handler: MultiSizeHandler)(implicit cs: ContextShift[IO])
  extends PicServiceT[IO] {
  private val log = Logger(getClass)

  override def save(tempFile: Path, by: BaseRequest, preferredName: Option[String]): IO[KeyMeta] = {
    val fileCopy: IO[(Path, Key)] = IO {
      // without dot
      val name = preferredName getOrElse tempFile.getFileName.toString
      val ext = Option(FilenameUtils.getExtension(name)).filter(_.nonEmpty).getOrElse("jpeg")
      val key = Keys.randomish().append(s".${ext.toLowerCase}")
      val renamedFile = tempFile resolveSibling s"$key"
      Files.copy(tempFile, renamedFile)
      log.trace(
        s"Copied temp file '$tempFile' to '$renamedFile', size ${Files.size(tempFile)} bytes."
      )
      (renamedFile, key)
    }
    fileCopy.flatMap { case (renamedFile, key) =>
      for {
        _ <- IO.fromFuture(IO(handler.handle(renamedFile, key)))
        meta <- db.saveMeta(key, by.name)
      } yield {
        meta
      }
    }

  }
}

object PicService {
  private val log = Logger(getClass)

  def apply(db: MetaSourceT[Future], handler: MultiSizeHandler): PicService =
    new PicService(db, handler)
}

class PicService(val metaDatabase: MetaSourceT[Future], val handler: MultiSizeHandler)
  extends PicServiceT[Future] {

  /** Resizes the image in `tempFile`, uploads it to S3 and saves image metadata in the database.
    *
    * Fails with Exception, ImageParseException, ...
    *
    * @param tempFile      original file
    * @param by            file owner
    * @param preferredName optional preferred file name - probably useless
    * @return metadata of the saved key
    */
  def save(tempFile: Path, by: BaseRequest, preferredName: Option[String]): Future[KeyMeta] = {
    // without dot
    val name = preferredName getOrElse tempFile.getFileName.toString
    val ext = Option(FilenameUtils.getExtension(name)).filter(_.nonEmpty).getOrElse("jpeg")
    val key = Keys.randomish().append(s".${ext.toLowerCase}")
    val renamedFile = tempFile resolveSibling s"$key"
    Files.copy(tempFile, renamedFile)
    log.trace(
      s"Copied temp file '$tempFile' to '$renamedFile', size ${Files.size(tempFile)} bytes."
    )
    for {
      _ <- handler.handle(renamedFile, key)
      meta <- metaDatabase.saveMeta(key, by.name)
    } yield {
      meta
    }
  }
}
