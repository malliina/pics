package com.malliina.pics

import java.util.UUID

import play.api.mvc.PathBindable

case class Key(key: String) {
  override def toString: String = key
}

object Key {
  val KeyLength = 7

  implicit object bindable extends PathBindable[Key] {
    override def bind(key: String, value: String): Either[String, Key] =
      if (value.length == KeyLength) Right(Key(value))
      else Left(s"Invalid key: $value")

    override def unbind(key: String, value: Key): String =
      value.key
  }

  def randomish(): Key = Key(UUID.randomUUID().toString.take(KeyLength).toLowerCase)
}

case class BucketName(name: String)

case class ContentType(contentType: String)
