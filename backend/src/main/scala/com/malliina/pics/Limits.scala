package com.malliina.pics

import org.http4s.Query

trait LimitsLike:
  def limit: Int
  def offset: Int

case class Limits(limit: Int, offset: Int)

object Limits:
  val Limit = "limit"
  val Offset = "offset"

  val DefaultLimit = 300
  val DefaultOffset = 0

  val default = Limits(DefaultLimit, DefaultOffset)

  import com.malliina.pics.http4s.QueryParsers.*

  def apply(q: Query): Either[Errors, Limits] = for
    limit <- parseOrDefault(q, Limit, DefaultLimit)
    offset <- parseOrDefault(q, Offset, DefaultOffset)
  yield Limits(limit, offset)
