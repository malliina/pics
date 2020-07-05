package com.malliina.pics.db

import play.api.Configuration

case class Conf(url: String, user: String, pass: String, driver: String)

object Conf {
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver

  def fromConf(conf: Configuration) = from(conf.get[Configuration]("pics.db"))

  def from(conf: Configuration): Either[String, Conf] = {
    def read(key: String) =
      conf.getOptional[String](key).toRight(s"Key missing: '$key'.")

    for {
      url <- read("url")
      user <- read("user")
      pass <- read("pass")
    } yield Conf(url, user, pass, read("driver").getOrElse(DefaultDriver))
  }
}
