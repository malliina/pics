package com.malliina.pics

import com.malliina.pics.Limits.DefaultLimit
import com.malliina.pics.http4s.QueryParsers.parseOrDefault
import org.http4s.Query

trait LimitsLike:
  def limit: Int
  def offset: Int

case class Limits(limit: Int, offset: Int):
  def prev = Limits(limit, offset - DefaultLimit)
  def next = Limits(limit, offset + DefaultLimit)

object Limits:
  val Limit = "limit"
  val Offset = "offset"

  val DefaultLimit = 300
  private val DefaultOffset = 0

  val default = Limits(DefaultLimit, DefaultOffset)

  def apply(q: Query): Either[Errors, Limits] = for
    limit <- parseOrDefault(q, Limit, DefaultLimit)
    offset <- parseOrDefault(q, Offset, DefaultOffset)
  yield Limits(limit, offset)
