package com.malliina.pics.db

import java.time.Instant

import com.malliina.pics.{Key, PicOwner}
import com.malliina.values.Username
import slick.jdbc.JdbcProfile

object Mappings {
  def apply(profile: JdbcProfile) = new Mappings(profile)
}

class Mappings(val profile: JdbcProfile) {

  import profile.api._

  implicit val instantMapping = MappedColumnType.base[Instant, java.sql.Timestamp](i => java.sql.Timestamp.from(i), _.toInstant)
  implicit val usernameMapping = MappedColumnType.base[Username, String](_.name, Username.apply)
  implicit val keyMapping = MappedColumnType.base[Key, String](k => k.key, Key.apply)
  implicit val ownerMapping = MappedColumnType.base[PicOwner, String](k => k.name, PicOwner.apply)
}
