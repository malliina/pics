package com.malliina.pics

case class ListRequest(limits: Limits, user: PicRequest) extends LimitsLike {
  def rh = user.rh

  override def limit: Int = limits.limit

  override def offset: Int = limits.offset
}

object ListRequest {
  def forRequest(req: PicRequest): Either[Errors, ListRequest] = for {
    limits <- Limits.forHeaders(req.rh)
  } yield ListRequest(limits, req)
}
