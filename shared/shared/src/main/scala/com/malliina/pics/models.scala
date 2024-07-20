package com.malliina.pics

import java.util.Date

import com.malliina.http.FullUrl
import com.malliina.values.{ValidatingCompanion, ErrorMessage, Readable}
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Json, Decoder, Encoder}
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

case class PicOwner(name: String) extends AnyVal:
  override def toString: String = name

object PicOwner extends ValidatingCompanion[String, PicOwner]:
  val anon: PicOwner = PicOwner("anon")

  override def build(input: String): Either[ErrorMessage, PicOwner] = Right(apply(input))

  override def write(t: PicOwner): String = t.name

case class ProfileInfo(user: PicOwner, readOnly: Boolean)

object ProfileInfo:
  val Welcome = "welcome"
  given Codec[ProfileInfo] = PicsJson.evented(Welcome, deriveCodec[ProfileInfo])

case class Key(key: String) extends AnyVal:
  override def toString: String = key

  def append(s: String): Key = Key(s"$key$s")

object Key:
  val Key = "key"
  val Length = 7

  given Codec[Key] =
    Codec.from(Decoder.decodeString.map(s => apply(s)), Encoder.encodeString.contramap(_.key))

  given Readable[Key] = Readable.string.map(apply)

object KeyParam:
  def unapply(str: String): Option[Key] =
    if str.trim.nonEmpty then Option(Key(str.trim)) else None

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
