package com.malliina.pics

import java.util.Date
import com.malliina.http.FullUrl
import com.malliina.values.Literals.err
import com.malliina.values.{ErrorMessage, Readable, Username, ValidatingCompanion}
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder, Json}
import io.circe.syntax.EncoderOps

enum Access(val name: String):
  case Public extends Access("public")
  case Private extends Access("private")

  override def toString: String = name

object Access:
  val FormKey = "access"
  given Codec[Access] = Codec.from(
    Decoder.decodeString.emap(s => parse(s).left.map(_.message)),
    Encoder.encodeString.contramap(_.name)
  )
  given Readable[Access] = Readable.string.emap(parse)

  def parse(in: String): Either[ErrorMessage, Access] =
    Access.values.find(v => v.name == in).toRight(ErrorMessage(s"Unknown access value: '$in'."))

  def parseUnsafe(in: String): Access =
    parse(in).fold(err => throw IllegalArgumentException(err.message), identity)

opaque type PicOwner = String

object PicOwner extends ValidatingCompanion[String, PicOwner]:
  val anon: PicOwner = "anon"
  val admin: PicOwner = "malliina123@gmail.com"

  override def build(input: String): Either[ErrorMessage, PicOwner] = Right(input)
  override def write(t: PicOwner): String = t
  extension (o: PicOwner) def name = write(o)
  def fromUser(username: Username): PicOwner = username.name

case class ProfileInfo(user: PicOwner, readOnly: Boolean)

object ProfileInfo:
  val Welcome = "welcome"
  given Codec[ProfileInfo] = PicsJson.evented(Welcome, deriveCodec[ProfileInfo])

opaque type Key = String

object Key:
  val Key = "key"
  val Length = 7

  extension (k: Key)
    def append(s: String): Key = s"$k$s"
    def key: String = k

  def build(input: String): Either[ErrorMessage, Key] =
    if input.isBlank then Left(err"Blank key not allowed.") else Right(input.trim)

  given Codec[Key] =
    Codec.from(
      Decoder.decodeString.emap(s => build(s).left.map(_.message)),
      Encoder.encodeString.contramap(_.key)
    )

  given Readable[Key] = Readable.string.emap(build)

object KeyParam:
  def unapply(str: String): Option[Key] =
    if str.trim.nonEmpty then Key.build(str).toOption else None

case class PicsRemoved(keys: Seq[Key])

object PicsRemoved:
  val Removed = "removed"
  given Codec[PicsRemoved] = PicsJson.evented(Removed, deriveCodec[PicsRemoved])

trait BaseMeta:
  def key: Key
  def added: java.util.Date
  def url: FullUrl
  def small: FullUrl
  def medium: FullUrl
  def large: FullUrl
  def access: Access

/** Using java.util.Date because Scala.js doesn't fully support java.time.* classes.
  */
case class PicMeta(
  key: Key,
  added: java.util.Date,
  url: FullUrl,
  small: FullUrl,
  medium: FullUrl,
  large: FullUrl,
  access: Access
) extends BaseMeta:
  def withClientKey(clientKey: Key): ClientPicMeta =
    ClientPicMeta(key, added, url, small, medium, large, clientKey, access)

object PicMeta:
  given dateFormat: Codec[Date] = Codec.from(
    Decoder.decodeLong.map(l => new Date(l)),
    Encoder.encodeLong.contramap(_.getTime)
  )
  given Codec[PicMeta] = deriveCodec[PicMeta]

case class Pics(pics: List[PicMeta]) derives Codec.AsObject

case class ClientPicMeta(
  key: Key,
  added: java.util.Date,
  url: FullUrl,
  small: FullUrl,
  medium: FullUrl,
  large: FullUrl,
  clientKey: Key,
  access: Access
) extends BaseMeta

object ClientPicMeta:
  given Codec[Date] = PicMeta.dateFormat
  given Codec[ClientPicMeta] = deriveCodec[ClientPicMeta]

case class PicsAdded(pics: Seq[ClientPicMeta])

object PicsAdded:
  val Added = "added"
  given Codec[PicsAdded] = PicsJson.evented(Added, deriveCodec[PicsAdded])

object PicsJson:
  val EventKey = "event"

  def evented[T](event: String, f: Codec[T]): Codec[T] = Codec.from(
    f,
    (t: T) => f(t).deepMerge(Json.obj(EventKey -> event.asJson))
  )
