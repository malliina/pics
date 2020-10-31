package com.malliina.pics

import org.http4s.Query
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

  import com.malliina.pics.http4s.QueryParsers._

  def apply(q: Query): Either[Errors, Limits] = for {
    limit <- parseOrDefault(q, Limit, DefaultLimit)
    offset <- parseOrDefault(q, Offset, DefaultOffset)
  } yield Limits(limit, offset)

  def forHeaders(rh: RequestHeader): Either[Errors, Limits] = for {
    limit <- read[Int](Limit, rh).getOrElse(Right(DefaultLimit))
    offset <- read[Int](Offset, rh).getOrElse(Right(DefaultOffset))
  } yield Limits(limit, offset)

  def read[A](key: String, rh: RequestHeader)(
    implicit basic: QueryStringBindable[A]
  ): Option[Either[Errors, A]] =
    basic.bind(key, rh.queryString).map(_.left.map(err => Errors.single(err)))
}
