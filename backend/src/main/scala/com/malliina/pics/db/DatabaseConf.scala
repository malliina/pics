package com.malliina.pics.db

import com.malliina.config.ConfigReadable
import com.malliina.pics.PicsConf.ConfigOps

case class DatabaseConf(
  url: String,
  user: String,
  pass: String,
  maxPoolSize: Int,
  migrateOnStart: Boolean
)

object DatabaseConf:
  val MySQLDriver = "com.mysql.cj.jdbc.Driver"

  implicit val config: ConfigReadable[DatabaseConf] = ConfigReadable.config.emap { obj =>
    for
      url <- obj.read[String]("url")
      user <- obj.read[String]("user")
      pass <- obj.read[String]("pass")
      poolSize <- obj.read[Int]("maxPoolSize")
      migrateOnStart <- obj.read[Boolean]("migrateOnStart")
    yield DatabaseConf(url, user, pass, poolSize, migrateOnStart)
  }
