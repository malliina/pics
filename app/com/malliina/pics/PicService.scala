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

  def apply(db: PicsDatabase, handler: MultiSizeHandler): PicService =
    new PicService(PicsMetaDatabase(db), handler)
}

class PicService(val metaDatabase: PicsMetaDatabase,
                 val handler: MultiSizeHandler) {

  /** Resizes the image in `tempFile`, uploads it to S3 and saves image metadata in the database.
    *
    * @param tempFile      original file
    * @param by            file owner
    * @param preferredName optional preferred file name - probably useless
    * @return metadata of the saved key
    */
  def save(tempFile: Path, by: Username, preferredName: Option[String]): Future[KeyMeta] = {
    // without dot
    val name = preferredName getOrElse tempFile.getFileName.toString
    val ext = Option(FilenameUtils.getExtension(name)).filter(_.nonEmpty).getOrElse("jpeg")
    val renamedFile = tempFile resolveSibling s"$name.$ext"
    Files.copy(tempFile, renamedFile)
    log.trace(s"Copied temp file '$tempFile' to '$renamedFile', size ${Files.size(tempFile)} bytes.")
    //    val thumbFile = renamedFile resolveSibling s"$name-thumb.$ext"
    val key = Keys.randomish().append(s".${ext.toLowerCase}")
    for {
      _ <- handler.handle(renamedFile, key)
      meta <- metaDatabase.saveMeta(key, by)
    } yield {
      meta
    }
  }
}
