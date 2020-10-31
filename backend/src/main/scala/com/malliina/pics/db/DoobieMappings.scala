package com.malliina.pics.db

import java.time.Instant

import com.malliina.pics.{Key, PicOwner}
import doobie.Meta

trait DoobieMappings {
  implicit val instantMapping: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  implicit val keyMapping: Meta[Key] = Meta[String].timap(Key.apply)(_.key)
  implicit val picOwnerMapping: Meta[PicOwner] = Meta[String].timap(PicOwner.apply)(_.name)
}
