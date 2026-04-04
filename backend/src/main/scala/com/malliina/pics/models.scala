package com.malliina.pics

import com.malliina.http.{FullUrl, SingleError}
import com.malliina.pics.auth.{Cognito, PicUser, SocialEmail, UserPayload}
import com.malliina.pics.http4s.{Reverse, Urls}
import com.malliina.values.Literals.err
import com.malliina.values.{Email, ErrorMessage, NonNeg}
import fs2.io.file.Path
import io.circe.Codec
import org.apache.commons.io.FilenameUtils
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}
import org.http4s.{Headers, Request, Uri}

import java.time.Instant
import java.util.Date

trait BaseRequest:
  def user: UserPayload
  def role: Role
  def language: Language
  def readOnly = role == Role.ReadOnly
  def isAdmin = role == Role.Admin
  def lang = Lang(language)
  def name = user.username

case class PicRequest(
  user: UserPayload,
  role: Role,
  language: Language,
  rh: Headers
) extends BaseRequest:
  def toUser: PicUser = PicUser(user, role, language)

object PicRequest:
  def anon(headers: Headers): PicRequest =
    PicRequest(UserPayload.anon, Role.ReadOnly, Language.default, headers)

  def user(user: PicUser, headers: Headers) =
    PicRequest(user.user, user.role, user.language, headers)

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
  def withUser(user: UserPayload) = KeyMeta(key, user, Access.Private, lastModified)

case class KeyMetaRow(
  key: Key,
  username: PicUsername,
  email: Option[Email],
  cognito: Option[CognitoUserId],
  access: Access,
  added: Instant
):
  def toMeta: Either[ErrorMessage, KeyMeta] =
    email
      .map(SocialEmail(_))
      .orElse(cognito.map(Cognito(_)))
      .map: sub =>
        KeyMeta(key, UserPayload(username, sub), access, added)
      .toRight(err"Missing both email and cognito identifier.")

case class KeyMeta(key: Key, owner: UserPayload, access: Access, added: Instant)

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

case class Conf(languages: Seq[Lang]) derives Codec.AsObject

object Conf:
  val default = Conf(Seq(Lang.en, Lang.se))
