package com.malliina.sitegen

import org.slf4j.{Logger, LoggerFactory}

object AppLogger {
  def apply(cls: Class[?]): Logger = {
    val name = cls.getName.reverse.dropWhile(_ == '$').reverse
    LoggerFactory.getLogger(name)
  }
}

sealed abstract class Mode(val name: String)

object Mode {
  case object Dev extends Mode("dev")
  case object Prod extends Mode("prod")
  val dev: Mode = Dev
  val prod: Mode = Prod
}
