package com.malliina.pics.db

import com.malliina.pics.{Key, PicOwner}
import com.malliina.values.Username
import doobie.Meta

import java.time.Instant

trait DoobieMappings {
  implicit val instantMapping: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  implicit val keyMapping: Meta[Key] = Meta[String].timap(Key.apply)(_.key)
  implicit val picOwnerMapping: Meta[PicOwner] = Meta[String].timap(PicOwner.apply)(_.name)
  implicit val usernameMapping: Meta[Username] = Meta[String].timap(Username.apply)(_.name)
}
