package com.malliina.pics

import java.nio.file.Path
import java.time.Instant

import buildinfo.BuildInfo
import com.malliina.http.FullUrl
import com.malliina.play.http.FullUrls
import com.malliina.play.models.Username
import controllers.routes
import org.apache.commons.io.FilenameUtils
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}
import play.api.http.Writeable
import play.api.libs.json._
import play.api.mvc.{PathBindable, RequestHeader}

case class AppMeta(name: String, version: String, gitHash: String)

object AppMeta {
  implicit val json = Json.format[AppMeta]

  val default = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.hash)
}

case class Key(key: String) {
  override def toString: String = key

  def append(s: String) = Key(s"$key$s")
}

object Key {
  val KeyLength = 7

  implicit val json = valueFormat[Key, String](_.validate[String].map(Key.apply), _.key)

  def valueFormat[A, B: Writes](read: JsValue => JsResult[A], write: A => B): Format[A] =
    Format[A](Reads(read), Writes(a => Json.toJson(write(a))))

  implicit object bindable extends PathBindable[Key] {
    override def bind(key: String, value: String): Either[String, Key] =
      if (value.length >= KeyLength) Right(Key(value))
      else Left(s"Invalid key: '$value'.")

    override def unbind(key: String, value: Key): String =
      value.key
  }

  val generator = new RandomStringGenerator.Builder()
    .withinRange('0', 'z')
    .filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS)
    .build()

  def randomish(): Key = Key(generator.generate(KeyLength).toLowerCase)
}

case class FlatMeta(key: Key, lastModified: Instant) {
  def withUser(user: Username) = KeyMeta(key, user, lastModified)
}

case class KeyMeta(key: Key, owner: Username, added: Instant) {
  def toEntry(rh: RequestHeader) = KeyEntry(this, rh)
}

case class KeyEntry(key: Key, added: Instant, url: FullUrl, thumb: FullUrl)

object KeyEntry {
  implicit val json = Json.format[KeyEntry]

  def apply(meta: KeyMeta, rh: RequestHeader): KeyEntry = {
    val host = FullUrls.hostOnly(rh)
    val key = meta.key
    KeyEntry(
      key,
      meta.added,
      FullUrls.absolute(host, routes.Home.pic(key)),
      FullUrls.absolute(host, routes.Home.thumb(key))
    )
  }
}

case class Pics(pics: Seq[KeyEntry])

object Pics {
  implicit val json = Json.format[Pics]
  implicit val html = Writeable.writeableOf_JsValue.map[Pics](ps => Json.toJson(ps))
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
