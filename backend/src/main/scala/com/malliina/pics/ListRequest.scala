package com.malliina.pics

case class ListRequest(limits: Limits, user: PicRequest) extends LimitsLike {
  override def limit: Int = limits.limit
  override def offset: Int = limits.offset
}
