package com.malliina.pics

import com.malliina.http.{FullUrl, SingleError}
import com.malliina.pics.http4s.{Reverse, Urls}
import com.malliina.values.{ErrorMessage, Username}
import fs2.io.file.Path
import io.circe.{Codec, Decoder, Encoder}
import org.apache.commons.io.FilenameUtils
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}
import org.http4s.{Headers, Request, Uri}

import java.time.Instant
import java.util.Date
import scala.annotation.targetName

opaque type NonNeg = Int

object NonNeg:
  given Codec[NonNeg] = Codec.from(
    Decoder.decodeInt.emap(i => apply(i).left.map(_.message)),
    Encoder.encodeInt.contramap(identity)
  )

  def apply(i: Int): Either[ErrorMessage, NonNeg] =
    if i >= 0 then Right(i)
    else Left(ErrorMessage(s"Value must be non-negative. Got '$i'."))

  extension (nn: NonNeg)
    def value: Int = nn
    def minus(other: Int): Either[ErrorMessage, NonNeg] = apply(value - other)
    def plus(other: Int): Either[ErrorMessage, NonNeg] = apply(value + other)
    @targetName("add")
    def +(other: NonNeg): NonNeg = value + other

trait BaseRequest:
  def name: PicOwner
  def readOnly: Boolean
  def isAnon = readOnly

case class PicRequest(name: PicOwner, readOnly: Boolean, rh: Headers) extends BaseRequest

object PicRequest:
  val AnonUser = PicOwner("anon")

  def anon(headers: Headers): PicRequest = PicRequest(AnonUser, true, headers)
  def forUser(user: Username, headers: Headers): PicRequest = apply(PicOwner(user.name), headers)
  def apply(user: PicOwner, headers: Headers): PicRequest =
    apply(PicOwner(user.name), user == AnonUser, headers)

case class ListRequest(limits: Limits, user: PicRequest) extends LimitsLike:
  override def limit: NonNeg = limits.limit
  override def offset: NonNeg = limits.offset

sealed trait PicResult

case class PicNotFound(key: Key) extends PicResult
case object PicSuccess extends PicResult

sealed abstract class PicSize(val value: String)

object PicSize:
  val Key = "s"

  def apply[F[_]](request: Request[F]): Either[SingleError, PicSize] =
    request.uri.query.params.get(Key).map(parse).getOrElse(Right(Original))

  def parse(in: String): Either[SingleError, PicSize] = in match
    case Small.value    => Right(Small)
    case Medium.value   => Right(Medium)
    case Large.value    => Right(Large)
    case Original.value => Right(Original)
    case other          => Left(SingleError.input(s"Invalid size: '$other'."))

  case object Small extends PicSize("s")
  case object Medium extends PicSize("m")
  case object Large extends PicSize("l")
  case object Original extends PicSize("o")

case class PicBundle(small: Path, medium: Path, large: Path, original: Path)

case class AppMeta(name: String, version: String, gitHash: String) derives Codec.AsObject

object AppMeta:
  val default = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash)

object Keys:
  private val generator = new RandomStringGenerator.Builder()
    .withinRange('0', 'z')
    .filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS)
    .get()

  def randomish(): Key = Key(generator.generate(Key.Length).toLowerCase)

case class FlatMeta(key: Key, lastModified: Instant):
  def withUser(user: PicOwner) = KeyMeta(key, user, Access.Private, lastModified)

case class KeyMeta(key: Key, owner: PicOwner, access: Access, added: Instant)

object PicMetas:
  def from[F[_]](meta: KeyMeta, rh: Request[F]): PicMeta = fromHost(meta, Urls.hostOnly(rh))

  private def fromHost(meta: KeyMeta, host: FullUrl): PicMeta =
    val key = meta.key
    PicMeta(
      key,
      Date.from(meta.added),
      host / Reverse.pic(meta.key).renderString,
      picUrl(key, PicSize.Small, host),
      picUrl(key, PicSize.Medium, host),
      picUrl(key, PicSize.Large, host),
      meta.access
    )

  private def picUrl(key: Key, size: PicSize, host: FullUrl) =
    host / query(Reverse.pic(key), PicSize.Key, size.value).renderString

  def query(call: Uri, key: String, value: String) = call.withQueryParam(key, value)

case class PicResponse(pic: PicMeta) derives Codec.AsObject

opaque type BucketName = String

object BucketName:
  def apply(s: String): BucketName = s
  extension (bn: BucketName) def name: String = bn

case class ContentType(contentType: String) extends AnyVal:
  def isImage = contentType.startsWith("image")

object ContentType:
  val ImageJpeg = image("jpeg")
  val ImagePng = image("png")
  val ImageBmp = image("bmp")
  val ImageGif = image("gif")
  val OctetStream = ContentType("application/octet-stream")

  def image(subName: String) = ContentType(s"image/$subName")

  def parseFile(path: Path) = parse(path.fileName.toString)

  def parse(name: String): Option[ContentType] =
    val attempt: PartialFunction[String, ContentType] =
      case "jpg"  => ImageJpeg
      case "jpeg" => ImageJpeg
      case "png"  => ImagePng
      case "gif"  => ImageGif
      case "bmp"  => ImageBmp
    attempt.lift(FilenameUtils.getExtension(name))
