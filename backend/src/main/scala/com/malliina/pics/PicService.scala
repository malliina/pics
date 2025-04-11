package com.malliina.pics

import cats.effect.Sync
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.pics.PicService.log
import com.malliina.util.AppLogger
import fs2.io.file.{Files, Path}
import org.apache.commons.io.FilenameUtils

trait PicServiceT[F[_]]:
  def save(tempFile: Path, by: BaseRequest, preferredName: Option[String]): F[KeyMeta]

object PicService:
  private val log = AppLogger(getClass)

class PicService[F[_]: {Sync, Files}](val db: MetaSourceT[F], handler: MultiSizeHandler[F])
  extends PicServiceT[F]:
  val F = Files[F]
  val S = Sync[F]

  override def save(tempFile: Path, by: BaseRequest, preferredName: Option[String]): F[KeyMeta] =
    val computeRenamedFile = S.delay:
      // without dot
      val name = preferredName.getOrElse(tempFile.fileName.toString)
      val ext = Option(FilenameUtils.getExtension(name)).filter(_.nonEmpty).getOrElse("jpeg")
      val key = Keys.randomish().append(s".${ext.toLowerCase}")
      val renamedFile = tempFile.resolveSibling(s"$key")
      (key, renamedFile)
    for
      (key, renamedFile) <- computeRenamedFile
      _ <- F.copy(tempFile, renamedFile)
      size <- F.size(tempFile)
      _ <- S.delay(
        log.trace(
          s"Copied temp file '$tempFile' to '$renamedFile', size $size bytes."
        )
      )
      _ <- handler.handle(renamedFile, key)
      meta <- db.saveMeta(key, by.name)
    yield meta
