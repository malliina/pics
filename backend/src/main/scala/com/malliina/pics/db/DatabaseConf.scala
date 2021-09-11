package com.malliina.pics.db

import com.malliina.config.ConfigReadable

case class DatabaseConf(url: String, user: String, pass: String)

object DatabaseConf {
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver

  import com.malliina.pics.PicsConf.ConfigOps

  implicit val config: ConfigReadable[DatabaseConf] = ConfigReadable.config.emap { obj =>
    for {
      url <- obj.read[String]("url")
      user <- obj.read[String]("user")
      pass <- obj.read[String]("pass")
    } yield DatabaseConf(url, user, pass)
  }
}
