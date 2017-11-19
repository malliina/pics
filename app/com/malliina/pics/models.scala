package com.malliina.pics

import java.nio.file.Path

import org.apache.commons.io.FilenameUtils
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}
import play.api.mvc.PathBindable

case class Key(key: String) {
  override def toString: String = key

  def append(s: String) = Key(s"$key$s")
}

object Key {
  val KeyLength = 7

  implicit object bindable extends PathBindable[Key] {
    override def bind(key: String, value: String): Either[String, Key] =
      if (value.length >= KeyLength) Right(Key(value))
      else Left(s"Invalid key: $value")

    override def unbind(key: String, value: Key): String =
      value.key
  }

  val generator = new RandomStringGenerator.Builder()
    .withinRange('0', 'z')
    .filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS)
    .build()

  def randomish(): Key = Key(generator.generate(KeyLength).toLowerCase)
}

case class BucketName(name: String)

case class ContentType(contentType: String) {
  def isImage = contentType startsWith "image"
}

object ContentType {
  val ImageJpeg = image("jpeg")
  val ImagePng = image("png")
  val ImageBmp = image("bmp")
  val ImageGif = image("gif")
  val OctetStream = ContentType("application/octet-stream")

  def image(subName: String) = ContentType(s"image/$subName")

  def parseFile(path: Path) = parse(path.getFileName.toString)

  def parse(name: String): Option[ContentType] = {
    val attempt: PartialFunction[String, ContentType] = {
      case "jpg" => ImageJpeg
      case "jpeg" => ImageJpeg
      case "png" => ImagePng
      case "gif" => ImageGif
      case "bmp" => ImageBmp
    }
    attempt.lift(FilenameUtils.getExtension(name))
  }
}
