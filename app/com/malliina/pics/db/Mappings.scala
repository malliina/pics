package com.malliina.pics.db

import java.time.Instant

import com.malliina.pics.Key
import com.malliina.play.models.Username
import slick.jdbc.H2Profile.api._

object Mappings {
  implicit val instantMapping = MappedColumnType.base[Instant, Long](_.toEpochMilli, Instant.ofEpochMilli)
  implicit val usernameMapping = MappedColumnType.base[Username, String](Username.raw, Username.apply)
  implicit val keyMapping = MappedColumnType.base[Key, String](k => k.key, Key.apply)
}
