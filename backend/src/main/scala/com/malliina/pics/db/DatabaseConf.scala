package com.malliina.pics.db

import play.api.Configuration

case class DatabaseConf(url: String, user: String, pass: String)

object DatabaseConf {
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver

  def fromConf(conf: Configuration) = fromDatabaseConf(conf.get[Configuration]("pics.db"))

  def fromDatabaseConf(conf: Configuration): Either[String, DatabaseConf] = {
    def read(key: String) =
      conf.getOptional[String](key).toRight(s"Key missing: '$key'.")

    for {
      url <- read("url")
      user <- read("user")
      pass <- read("pass")
    } yield DatabaseConf(url, user, pass)
  }
}
