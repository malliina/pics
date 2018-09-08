package com.malliina.pics

import java.nio.file.{Files, Path}

import com.malliina.concurrent.Execution.cached
import com.malliina.pics.PicService.log
import com.malliina.pics.db.{PicsDatabase, PicsMetaDatabase}
import org.apache.commons.io.FilenameUtils
import play.api.Logger

import scala.concurrent.Future

object PicService {
  private val log = Logger(getClass)

  def apply(db: PicsDatabase, handler: MultiSizeHandler): PicService =
    new PicService(PicsMetaDatabase(db), handler)
}

class PicService(val metaDatabase: PicsMetaDatabase, val handler: MultiSizeHandler) {
  /** Resizes the image in `tempFile`, uploads it to S3 and saves image metadata in the database.
    *
    * Fails with Exception, ImageParseException, ...
    *
    * @param tempFile      original file
    * @param by            file owner
    * @param preferredName optional preferred file name - probably useless
    * @return metadata of the saved key
    */
  def save(tempFile: Path, by: PicRequest, preferredName: Option[String]): Future[KeyMeta] = {
    // without dot
    val name = preferredName getOrElse tempFile.getFileName.toString
    val ext = Option(FilenameUtils.getExtension(name)).filter(_.nonEmpty).getOrElse("jpeg")
    val key = Keys.randomish().append(s".${ext.toLowerCase}")
    val renamedFile = tempFile resolveSibling s"$key"
    Files.copy(tempFile, renamedFile)
    log.trace(s"Copied temp file '$tempFile' to '$renamedFile', size ${Files.size(tempFile)} bytes.")
    //    val thumbFile = renamedFile resolveSibling s"$name-thumb.$ext"
    for {
      _ <- handler.handle(renamedFile, key)
      meta <- metaDatabase.saveMeta(key, by.name)
    } yield {
      meta
    }
  }
}
