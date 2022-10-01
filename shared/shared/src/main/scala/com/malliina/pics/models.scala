package com.malliina.pics

import java.util.Date

import com.malliina.http.FullUrl
import com.malliina.values.{ValidatingCompanion, ErrorMessage}
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Json, Decoder, Encoder}
import io.circe.syntax.EncoderOps

enum Access(val name: String):
  case Public extends Access("public")
  case Private extends Access("private")

object Access:
  implicit val json: Codec[Access] = Codec.from(
    Decoder.decodeString.emap(parse),
    Encoder.encodeString.contramap(_.name)
  )

  def parse(in: String): Either[String, Access] =
    Access.values.find(v => v.name == in).toRight(s"Unknown access value: '$in'.")

  def parseUnsafe(in: String): Access =
    parse(in).fold(err => throw new IllegalArgumentException(err), identity)

case class PicOwner(name: String) extends AnyVal:
  override def toString: String = name

object PicOwner extends ValidatingCompanion[String, PicOwner]:
  val anon: PicOwner = PicOwner("anon")

  override def build(input: String): Either[ErrorMessage, PicOwner] = Right(apply(input))

  override def write(t: PicOwner): String = t.name

case class ProfileInfo(user: PicOwner, readOnly: Boolean)

object ProfileInfo:
  val Welcome = "welcome"
  implicit val json: Codec[ProfileInfo] = PicsJson.evented(Welcome, deriveCodec[ProfileInfo])

case class Key(key: String) extends AnyVal:
  override def toString: String = key

  def append(s: String): Key = Key(s"$key$s")

object Key:
  val Length = 7

  implicit val json: Codec[Key] =
    Codec.from(Decoder.decodeString.map(s => apply(s)), Encoder.encodeString.contramap(_.key))

object KeyParam:
  def unapply(str: String): Option[Key] =
    if str.trim.nonEmpty then Option(Key(str.trim)) else None

case class PicsRemoved(keys: Seq[Key])

object PicsRemoved:
  val Removed = "removed"
  implicit val json: Codec[PicsRemoved] = PicsJson.evented(Removed, deriveCodec[PicsRemoved])

trait BaseMeta:
  def key: Key
  def added: java.util.Date
  def url: FullUrl
  def small: FullUrl
  def medium: FullUrl
  def large: FullUrl

/** Using java.util.Date because Scala.js doesn't fully support java.time.* classes.
  */
case class PicMeta(
  key: Key,
  added: java.util.Date,
  url: FullUrl,
  small: FullUrl,
  medium: FullUrl,
  large: FullUrl
) extends BaseMeta:
  def withClientKey(clientKey: Key): ClientPicMeta =
    ClientPicMeta(key, added, url, small, medium, large, clientKey)

object PicMeta:
  implicit val dateFormat: Codec[Date] = Codec.from(
    Decoder.decodeLong.map(l => new Date(l)),
    Encoder.encodeLong.contramap(_.getTime)
  )
  implicit val json: Codec[PicMeta] = deriveCodec[PicMeta]

case class Pics(pics: List[PicMeta])

object Pics:
  implicit val json: Codec[Pics] = deriveCodec[Pics]

case class ClientPicMeta(
  key: Key,
  added: java.util.Date,
  url: FullUrl,
  small: FullUrl,
  medium: FullUrl,
  large: FullUrl,
  clientKey: Key
) extends BaseMeta

object ClientPicMeta:
  implicit val dateformat: Codec[Date] = PicMeta.dateFormat
  implicit val json: Codec[ClientPicMeta] = deriveCodec[ClientPicMeta]

case class PicsAdded(pics: Seq[ClientPicMeta])

object PicsAdded:
  val Added = "added"
  implicit val json: Codec[PicsAdded] = PicsJson.evented(Added, deriveCodec[PicsAdded])

object PicsJson:
  val EventKey = "event"

  def evented[T](event: String, f: Codec[T]): Codec[T] = Codec.from(
    f,
    (t: T) => f(t).deepMerge(Json.obj(EventKey -> event.asJson))
  )
