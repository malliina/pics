package com.malliina.pics.db

import play.api.Configuration

case class Conf(url: String, user: String, pass: String, driver: String)

object Conf {
  val UrlKey = "db_url"
  val UserKey = "db_user"
  val PassKey = "db_pass"
  val DriverKey = "db_driver"
  val DefaultDriver = "org.mariadb.jdbc.Driver"

  def fromEnvOrFail() = fromEnv().fold(err => throw new Exception(err), identity)

  def fromConf(conf: Configuration) = from(key => conf.getOptional[String](key))

  def fromEnv() = from(key => sys.env.get(key).orElse(sys.props.get(key)))

  def from(readKey: String => Option[String]) = {
    def read(key: String) = readKey(key)
      .toRight(s"Key missing: '$key'.")

    for {
      url <- read(UrlKey)
      user <- read(UserKey)
      pass <- read(PassKey)
    } yield Conf(url, user, pass, read(DriverKey).getOrElse(DefaultDriver))
  }
}
