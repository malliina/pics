package com.malliina.pics.db

case class DatabaseConf(url: String, user: String, pass: String)

object DatabaseConf {
  val MySQLDriver = "com.mysql.jdbc.Driver"
  val DefaultDriver = MySQLDriver
}
