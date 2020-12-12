package com.malliina.pics.app

import java.nio.file.Paths

import com.typesafe.config.ConfigFactory

object LocalConf {
  val appDir = Paths.get(sys.props("user.home")).resolve(".pics")
  val localConfFile = appDir.resolve("pics.conf")
  val localConfig = ConfigFactory.parseFile(localConfFile.toFile)
}
