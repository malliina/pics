package com.malliina.pics

import java.nio.file.Path
import java.time.Instant
import java.util.Date

import com.malliina.http.FullUrl
import com.malliina.pics.PicRequest.AnonUser
import com.malliina.pics.http4s.{Reverse, Urls}
import com.malliina.play.http.FullUrls
import com.malliina.values.Username
import org.apache.commons.io.FilenameUtils
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}
import org.http4s.{Headers, Request, Uri}
import play.api.http.Writeable
import play.api.libs.json._
import play.api.mvc.{PathBindable, RequestHeader}

trait BaseRequest {
  def name: PicOwner
  def readOnly: Boolean
  def isAnon = readOnly
}

case class PicRequest2(name: PicOwner, readOnly: Boolean, rh: Headers) extends BaseRequest

object PicRequest2 {
  def anon(headers: Headers): PicRequest2 = PicRequest2(PicRequest.AnonUser, true, headers)
  def forUser(user: Username, headers: Headers): PicRequest2 = apply(PicOwner(user.name), headers)
  def apply(user: PicOwner, headers: Headers): PicRequest2 =
    apply(PicOwner(user.name), user == AnonUser, headers)
}

case class PicRequest(name: PicOwner, readOnly: Boolean, rh: RequestHeader) extends BaseRequest

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

  def apply[F[_]](request: Request[F]): Either[SingleError, PicSize] =
    request.uri.query.params.get(Key).map(parse).getOrElse(Right(Original))

  def apply(rh: RequestHeader): Either[SingleError, PicSize] =
    rh.getQueryString(Key).map(parse).getOrElse(Right(Original))

  def parse(in: String): Either[SingleError, PicSize] = in match {
    case Small.value    => Right(Small)
    case Medium.value   => Right(Medium)
    case Large.value    => Right(Large)
    case Original.value => Right(Original)
    case other          => Left(SingleError(s"Invalid size: '$other'."))
  }

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
//  val reverse = routes.PicsController

  def apply(meta: KeyMeta, rh: RequestHeader): PicMeta = fromHost(meta, FullUrls.hostOnly(rh))

  def from[F[_]](meta: KeyMeta, rh: Request[F]): PicMeta = fromHost(meta, Urls.hostOnly(rh))

  def fromHost(meta: KeyMeta, host: FullUrl): PicMeta = {
    val key = meta.key
    PicMeta(
      key,
      Date.from(meta.added),
      host / Reverse.pic(meta.key).renderString,
      picUrl(key, PicSize.Small, host),
      picUrl(key, PicSize.Medium, host),
      picUrl(key, PicSize.Large, host)
    )
  }

  def picUrl(key: Key, size: PicSize, host: FullUrl) =
    host / query(Reverse.pic(key), PicSize.Key, size.value).renderString

  def query(call: Uri, key: String, value: String) = call.withQueryParam(key, value)
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
