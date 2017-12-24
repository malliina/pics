package com.malliina.pics

import java.nio.file.{Files, Path}

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.PicService.log
import com.malliina.pics.db.{PicsDatabase, PicsMetaDatabase}
import com.malliina.play.models.Username
import org.apache.commons.io.FilenameUtils
import play.api.Logger

import scala.concurrent.Future

object PicService {
  private val log = Logger(getClass)

  def apply(db: PicsDatabase, sources: PicSources): PicService =
    new PicService(
      PicsMetaDatabase(db),
      sources,
      PicsResizer.default
    )
}

class PicService(val metaDatabase: PicsMetaDatabase,
                 val sources: PicSources,
                 resizer: PicsResizer) {

  def save(tempFile: Path, by: Username, preferredName: Option[String]): Future[Either[ImageFailure, KeyMeta]] = {
    // without dot
    val name = preferredName getOrElse tempFile.getFileName.toString
    val ext = Option(FilenameUtils.getExtension(name)).filter(_.nonEmpty).getOrElse("jpeg")
    val renamedFile = tempFile resolveSibling s"$name.$ext"
    Files.copy(tempFile, renamedFile)
    log.trace(s"Copied temp file '$tempFile' to '$renamedFile', size ${Files.size(tempFile)} bytes.")
    //    val thumbFile = renamedFile resolveSibling s"$name-thumb.$ext"
    resizer.resize(renamedFile).fold(
      e => Future.successful(Left(e)),
      bundle => {
        val key = Key.randomish().append(s".${ext.toLowerCase}")
        for {
          _ <- sources.save(key, bundle)
          meta <- metaDatabase.saveMeta(key, by)
        } yield {
          Right(meta)
        }
      }
    )
  }
}
