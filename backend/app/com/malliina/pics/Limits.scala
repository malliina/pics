package com.malliina.pics

import play.api.mvc.{QueryStringBindable, RequestHeader}

trait LimitsLike {
  def limit: Int

  def offset: Int
}

case class Limits(limit: Int, offset: Int)

object Limits {
  val Limit = "limit"
  val Offset = "offset"

  val DefaultLimit = 300
  val DefaultOffset = 0

  val default = Limits(DefaultLimit, DefaultOffset)

  def forHeaders(rh: RequestHeader): Either[Errors, Limits] = for {
    limit <- read[Int](Limit, rh).getOrElse(Right(DefaultLimit))
    offset <- read[Int](Offset, rh).getOrElse(Right(DefaultOffset))
  } yield Limits(limit, offset)

  def read[A](key: String, rh: RequestHeader)(implicit basic: QueryStringBindable[A]): Option[Either[Errors, A]] =
    basic.bind(key, rh.queryString).map(_.left.map(err => Errors(Seq(SingleError(err)))))
}
