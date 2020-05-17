package com.malliina.pics

import java.nio.file.Path
import java.time.Instant
import java.util.Date

import com.malliina.http.FullUrl
import com.malliina.play.http.FullUrls
import com.malliina.values.Username
import controllers.routes
import org.apache.commons.io.FilenameUtils
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}
import play.api.http.Writeable
import play.api.libs.json._
import play.api.mvc.{Call, PathBindable, RequestHeader}

case class PicRequest(name: PicOwner, readOnly: Boolean, rh: RequestHeader) {
  def isAnon = readOnly
}

object PicRequest {
  val AnonUser = PicOwner("anon")

  def anon(rh: RequestHeader) = PicRequest(AnonUser, rh)

  def forUser(user: Username, rh: RequestHeader): PicRequest =
    apply(PicOwner(user.name), rh)

  def apply(user: PicOwner, rh: RequestHeader): PicRequest =
    PicRequest(user, readOnly = user == AnonUser, rh)
}

sealed trait PicResult

case class PicNotFound(key: Key) extends PicResult
case object PicSuccess extends PicResult

sealed abstract class PicSize(val value: String)

object PicSize {
  val Key = "s"

  def apply(rh: RequestHeader): Either[SingleError, PicSize] =
    rh.getQueryString(Key)
      .map {
        case Small.value    => Right(Small)
        case Medium.value   => Right(Medium)
        case Large.value    => Right(Large)
        case Original.value => Right(Original)
        case other          => Left(SingleError(s"Invalid size: '$other'."))
      }
      .getOrElse(Right(Original))

  case object Small extends PicSize("s")
  case object Medium extends PicSize("m")
  case object Large extends PicSize("l")
  case object Original extends PicSize("o")
}

case class PicBundle(small: Path, medium: Path, large: Path, original: Path)

case class AppMeta(name: String, version: String, gitHash: String)

object AppMeta {
  implicit val json = Json.format[AppMeta]

  val default = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.hash)
}

object Keys {

  implicit object bindable extends PathBindable[Key] {
    override def bind(key: String, value: String): Either[String, Key] =
      if (value.length >= Key.Length) Right(Key(value))
      else Left(s"Invalid key: '$value'.")

    override def unbind(key: String, value: Key): String =
      value.key
  }

  val generator = new RandomStringGenerator.Builder()
    .withinRange('0', 'z')
    .filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS)
    .build()

  def randomish(): Key = Key(generator.generate(Key.Length).toLowerCase)
}

case class FlatMeta(key: Key, lastModified: Instant) {
  def withUser(user: PicOwner) = KeyMeta(key, user, lastModified)
}

case class KeyMeta(key: Key, owner: PicOwner, added: Instant) {
  def toEntry(rh: RequestHeader) = PicMetas(this, rh)
}

object PicMetas {
  val reverse = routes.PicsController

  def apply(meta: KeyMeta, rh: RequestHeader): PicMeta = {
    val host = FullUrls.hostOnly(rh)
    val key = meta.key
    PicMeta(
      key,
      Date.from(meta.added),
      FullUrls.absolute(host, reverse.pic(key)),
      picUrl(key, PicSize.Small, host),
      picUrl(key, PicSize.Medium, host),
      picUrl(key, PicSize.Large, host)
    )
  }

  def picUrl(key: Key, size: PicSize, host: FullUrl) =
    FullUrls.absolute(host, query(reverse.pic(key), PicSize.Key, size.value))

  def query(call: Call, key: String, value: String) = call.copy(url = s"${call.url}?$key=$value")
}

case class PicResponse(pic: PicMeta)

object PicResponse {
  implicit val json = Json.format[PicResponse]
  implicit val html = Writeable.writeableOf_JsValue.map[PicResponse](ps => Json.toJson(ps))
}

object PicsWriteables {
  implicit val html = Writeable.writeableOf_JsValue.map[Pics](ps => Json.toJson(ps))
}

case class BucketName(name: String) extends AnyVal {
  override def toString = name
}

case class ContentType(contentType: String) extends AnyVal {
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
      case "jpg"  => ImageJpeg
      case "jpeg" => ImageJpeg
      case "png"  => ImagePng
      case "gif"  => ImageGif
      case "bmp"  => ImageBmp
    }
    attempt.lift(FilenameUtils.getExtension(name))
  }
}
