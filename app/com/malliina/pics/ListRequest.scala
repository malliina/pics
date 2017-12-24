package com.malliina.pics

import com.malliina.play.http.AuthedRequest
import com.malliina.play.models.Username
import play.api.mvc.RequestHeader

case class ListRequest(limits: Limits, user: Username, rh: RequestHeader) extends LimitsLike {
  override def limit: Int = limits.limit

  override def offset: Int = limits.offset
}

object ListRequest {
  def forRequest(req: AuthedRequest): Either[Errors, ListRequest] = for {
    limits <- Limits.forHeaders(req.rh)
  } yield ListRequest(limits, req.user, req.rh)
}
