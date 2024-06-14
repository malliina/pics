package com.malliina.pics.db

import com.malliina.pics.{Access, Key, NonNeg, PicOwner, getUnsafe}
import com.malliina.values.Username
import doobie.Meta

import java.time.Instant

trait DoobieMappings:
  given Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  given Meta[Key] = Meta[String].timap(Key.apply)(_.key)
  given Meta[PicOwner] = Meta[String].timap(PicOwner.apply)(_.name)
  given Meta[Username] = Meta[String].timap(Username.apply)(_.name)
  given Meta[Access] = Meta[String].timap(Access.parseUnsafe)(_.name)
  given Meta[NonNeg] = Meta[Int].timap(i => NonNeg(i).getUnsafe)(_.value)
