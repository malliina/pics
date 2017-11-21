package com.malliina.pics

import com.malliina.play.http.AuthedRequest
import com.malliina.play.models.Username
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

case class ListRequest(limits: Limits, user: Username, rh: RequestHeader) extends LimitsLike {
  override def limit: Int = limits.limit

  override def offset: Int = limits.offset
}

object ListRequest {
  def forRequest(req: AuthedRequest): Either[Errors, ListRequest] = for {
    limits <- Limits.forHeaders(req.rh)
  } yield ListRequest(limits, req.user, req.rh)
}
