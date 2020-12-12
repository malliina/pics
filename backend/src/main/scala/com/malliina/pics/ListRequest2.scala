package com.malliina.pics

case class ListRequest2(limits: Limits, user: PicRequest2) extends LimitsLike {
  override def limit: Int = limits.limit
  override def offset: Int = limits.offset
}
