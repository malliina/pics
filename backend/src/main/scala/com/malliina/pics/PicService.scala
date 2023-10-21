package com.malliina.pics

import java.nio.file.{Files, Path}

import cats.effect.*
import cats.syntax.all.*
import com.malliina.util.AppLogger
import org.apache.commons.io.FilenameUtils

trait PicServiceT[F[_]]:
  def save(tempFile: Path, by: BaseRequest, preferredName: Option[String]): F[KeyMeta]

class PicService[F[_]: Sync](val db: MetaSourceT[F], handler: MultiSizeHandler[F])
  extends PicServiceT[F]:
  private val log = AppLogger(getClass)

  override def save(tempFile: Path, by: BaseRequest, preferredName: Option[String]): F[KeyMeta] =
    val fileCopy: F[(Path, Key)] = Sync[F].delay:
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
    fileCopy.flatMap: (renamedFile, key) =>
      for
        _ <- handler.handle(renamedFile, key)
        meta <- db.saveMeta(key, by.name)
      yield meta
