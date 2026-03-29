package com.malliina.pics

import com.malliina.http.{FullUrl, SingleError}
import com.malliina.pics.http4s.{Reverse, Urls}
import com.malliina.values.NonNeg
import fs2.io.file.Path
import io.circe.Codec
import org.apache.commons.io.FilenameUtils
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}
import org.http4s.{Headers, Request, Uri}

import java.time.Instant
import java.util.Date

trait BaseRequest:
  def name: PicUsername
  def readOnly: Boolean
  def isAnon = readOnly

case class PicRequest(name: PicUsername, role: Role, rh: Headers) extends BaseRequest:
  def readOnly = role == Role.ReadOnly
  def isAdmin = role == Role.Admin

object PicRequest:
  def anon(headers: Headers): PicRequest =
    PicRequest(PicUsername.anon, Role.ReadOnly, headers)

case class ListRequest(limits: Limits, user: PicRequest) extends LimitsLike:
  override def limit: NonNeg = limits.limit
  override def offset: NonNeg = limits.offset

enum PicResult:
  case PicNotFound(key: Key) extends PicResult
  case PicSuccess extends PicResult

enum PicSize(val value: String):
  case Small extends PicSize("s")
  case Medium extends PicSize("m")
  case Large extends PicSize("l")
  case Original extends PicSize("o")

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

case class PicBundle(small: Path, medium: Path, large: Path, original: Path)

case class AppMeta(name: String, version: String, gitHash: String) derives Codec.AsObject

object AppMeta:
  val default = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash)

object Keys:
  private val generator = new RandomStringGenerator.Builder()
    .withinRange('0', 'z')
    .filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS)
    .get()

  def randomish(): Key = Key.build(generator.generate(Key.Length).toLowerCase).toOption.get

case class FlatMeta(key: Key, lastModified: Instant):
  def withUser(user: PicUsername) = KeyMeta(key, user, Access.Private, lastModified)

case class KeyMeta(key: Key, owner: PicUsername, access: Access, added: Instant)

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
