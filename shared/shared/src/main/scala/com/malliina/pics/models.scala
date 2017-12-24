package com.malliina.pics

import java.util.Date

import com.malliina.http.FullUrl
import play.api.libs.json._

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

/** Using java.util.Date because Scala.js doesn't fully support java.time.* classes.
  */
case class PicMeta(key: Key,
                   added: java.util.Date,
                   url: FullUrl,
                   small: FullUrl,
                   medium: FullUrl,
                   large: FullUrl)

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
