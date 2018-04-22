package com.malliina.pics.db

case class Conf(url: String, user: String, pass: String, driver: String)

object Conf {
  val UrlKey = "db_url"
  val UserKey = "db_user"
  val PassKey = "db_pass"
  val DriverKey = "db_driver"
  val DefaultDriver = "org.mariadb.jdbc.Driver"

  def read(key: String) = sys.env.get(key).orElse(sys.props.get(key))
    .toRight(s"Key missing: '$key'. Set it as an environment variable or system property.")

  def fromEnvOrFail() = fromEnv().fold(err => throw new Exception(err), identity)

  def fromEnv() = for {
    url <- read(UrlKey)
    user <- read(UserKey)
    pass <- read(PassKey)
  } yield Conf(url, user, pass, read(DriverKey).getOrElse(DefaultDriver))
}
