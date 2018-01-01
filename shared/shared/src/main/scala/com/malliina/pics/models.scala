package com.malliina.pics

import java.util.Date

import com.malliina.http.FullUrl
import com.malliina.json.ValidatingCompanion
import play.api.libs.json._

case class PicOwner(name: String)

object PicOwner extends ValidatingCompanion[String, PicOwner] {
  val anon = PicOwner("anon")

  override def build(input: String): Option[PicOwner] = Option(apply(input))

  override def write(t: PicOwner): String = t.name
}

case class ProfileInfo(user: PicOwner, readOnly: Boolean)

object ProfileInfo {
  val Welcome = "welcome"
  implicit val json = PicsJson.evented(Welcome, Json.format[ProfileInfo])
}

case class Key(key: String) {
  override def toString: String = key

  def append(s: String) = Key(s"$key$s")
}

object Key {
  val Length = 7

  implicit val json = valueFormat[Key, String](_.validate[String].map(Key.apply), _.key)

  def valueFormat[A, B: Writes](read: JsValue => JsResult[A], write: A => B): Format[A] =
    Format[A](Reads(read), Writes(a => Json.toJson(write(a))))
}

case class PicKeys(keys: Seq[Key])

object PicKeys {
  val Removed = "removed"
  implicit val json = PicsJson.evented(Removed, Json.format[PicKeys])
}

trait BaseMeta {
  def key: Key

  def added: java.util.Date

  def url: FullUrl

  def small: FullUrl

  def medium: FullUrl

  def large: FullUrl
}

/** Using java.util.Date because Scala.js doesn't fully support java.time.* classes.
  */
case class PicMeta(key: Key,
                   added: java.util.Date,
                   url: FullUrl,
                   small: FullUrl,
                   medium: FullUrl,
                   large: FullUrl) extends BaseMeta {
  def withClient(clientKey: Key): ClientPicMeta =
    ClientPicMeta(key, added, url, small, medium, large, clientKey)
}

object PicMeta {
  implicit val dateFormat = Format[Date](
    Reads[Date](_.validate[Long].map(l => new Date(l))),
    Writes[Date](d => Json.toJson(d.getTime))
  )
  implicit val json = Json.format[PicMeta]
}

case class Pics(pics: Seq[PicMeta])

object Pics {
  implicit val json = Json.format[Pics]
}

case class ClientPicMeta(key: Key,
                         added: java.util.Date,
                         url: FullUrl,
                         small: FullUrl,
                         medium: FullUrl,
                         large: FullUrl,
                         clientKey: Key) extends BaseMeta

object ClientPicMeta {
  implicit val dateformat = PicMeta.dateFormat
  implicit val json = Json.format[ClientPicMeta]
}

case class ClientPics(pics: Seq[ClientPicMeta])

object ClientPics {
  val Added = "added"
  implicit val json: Format[ClientPics] = PicsJson.evented(Added, Json.format[ClientPics])
}

object PicsJson {
  val EventKey = "event"

  def evented[T](event: String, f: OFormat[T]): Format[T] = {
    val withEvent = Writes[T] { t =>
      Json.obj(EventKey -> event) ++ f.writes(t)
    }
    Format[T](f, withEvent)
  }
}
