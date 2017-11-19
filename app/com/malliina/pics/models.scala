package com.malliina.pics

import java.nio.file.Path
import javax.activation.MimetypesFileTypeMap

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

case class ContentType(contentType: String)

object ContentType {
  private val mimeMap = new MimetypesFileTypeMap()

  val ImageJpeg = ContentType("image/jpeg")
  val OctetStream = ContentType("application/octet-stream")

  def parseFile(path: Path) = parse(path.getFileName.toString)

  def parse(name: String) = Option(mimeMap.getContentType(name)).map(ContentType.apply)
}