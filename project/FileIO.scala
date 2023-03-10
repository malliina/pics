package com.malliina.sitegen

import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.apache.commons.codec.digest.DigestUtils

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._
import java.util.Base64
import java.util.zip.GZIPOutputStream

object FileIO {
  val log = AppLogger(getClass)
  private val utf8 = StandardCharsets.UTF_8
  private val fallbackContentType = "application/octet-stream"

  def dataUri(file: Path): String = {
    val contentType = Option(Files.probeContentType(file)).getOrElse(fallbackContentType)
    s"data:$contentType;base64,${base64(file)}"
  }

  def base64(file: Path) = Base64.getEncoder.encodeToString(Files.readAllBytes(file))

  def writeJson[T: Encoder](t: T, to: Path): Path =
    write(t.asJson.spaces2.getBytes(utf8), to)

  def writeLines(lines: Seq[String], to: Path): Path =
    write(lines.mkString("\n").getBytes(utf8), to)

  def copyIfChanged(from: Path, to: Path): Boolean = {
    val changed = !Files.exists(to) || Files.mismatch(from, to) != -1L
    if (changed) copy(from, to)
    changed
  }

  def writeIfChanged(content: String, to: Path): Boolean = {
    val changed = mismatch(content, to)
    if (changed) write(content.getBytes(utf8), to)
    changed
  }

  def mismatch(content: String, file: Path) = !isSameContent(content, file)

  def isSameContent(content: String, file: Path) =
    if (Files.exists(file)) {
      val oldHash = md5(file)
      val newHash = DigestUtils.md5Hex(content)
      oldHash == newHash
    } else false

  def md5(file: Path) = DigestUtils.md5Hex(Files.readAllBytes(file))

  def write(bytes: Array[Byte], to: Path): Path = {
    if (!Files.isRegularFile(to)) {
      val dir = to.getParent
      if (!Files.isDirectory(dir))
        Files.createDirectories(dir)
      Files.createFile(to)
    }
    Files.write(to, bytes, StandardOpenOption.TRUNCATE_EXISTING)
    log.info(s"Wrote ${to.toAbsolutePath}.")
    to
  }

  def copy(from: Path, to: Path): Unit = {
    val dir = to.getParent
    if (!Files.isDirectory(dir))
      Files.createDirectories(dir)
    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
    log.info(s"Copied ${from.toAbsolutePath} to ${to.toAbsolutePath}.")
  }

  def gzip(src: Path, dest: Path): Unit =
    using(new FileInputStream(src.toFile)) { in =>
      using(new FileOutputStream(dest.toFile)) { out =>
        using(new GZIPOutputStream(out, 8192)) { gzip =>
          copyStream(in, gzip)
          gzip.finish()
        }
      }
    }

  // Adapted from sbt-io
  private def copyStream(in: InputStream, out: OutputStream): Unit = {
    val buffer = new Array[Byte](8192)

    def read(): Unit = {
      val byteCount = in.read(buffer)
      if (byteCount >= 0) {
        out.write(buffer, 0, byteCount)
        read()
      }
    }

    read()
  }

  // https://stackoverflow.com/a/27917071
  def deleteDirectory(dir: Path): Path = {
    if (Files.exists(dir)) {
      Files.walkFileTree(
        dir,
        new SimpleFileVisitor[Path] {
          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            Files.delete(file)
            FileVisitResult.CONTINUE
          }

          override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
            Files.delete(dir)
            FileVisitResult.CONTINUE
          }
        }
      )
    } else {
      dir
    }
  }

  def using[T <: AutoCloseable, U](res: T)(code: T => U): U =
    try code(res)
    finally res.close()
}
