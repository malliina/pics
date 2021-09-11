package com.malliina.pics

import java.nio.file.{Files, Path}

import cats.effect.*
import com.malliina.util.AppLogger
import org.apache.commons.io.FilenameUtils

trait PicServiceT[F[_]]:
  def save(tempFile: Path, by: BaseRequest, preferredName: Option[String]): F[KeyMeta]

class PicServiceIO(val db: MetaSourceT[IO], handler: MultiSizeHandlerIO) extends PicServiceT[IO]:
  private val log = AppLogger(getClass)

  override def save(tempFile: Path, by: BaseRequest, preferredName: Option[String]): IO[KeyMeta] =
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
      for
        _ <- handler.handle(renamedFile, key)
        meta <- db.saveMeta(key, by.name)
      yield meta
    }
