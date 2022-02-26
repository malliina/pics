package com.malliina.pics.app

import com.malliina.pics.BuildInfo

import java.nio.file.{Path, Paths}
import com.typesafe.config.{Config, ConfigFactory}

object LocalConf:
  val appDir: Path = Paths.get(sys.props("user.home")).resolve(".pics")
  val localConfFile: Path = appDir.resolve("pics.conf")
  val isProd: Boolean = BuildInfo.mode == "prod"
  private val localConfig: Config =
    ConfigFactory.parseFile(localConfFile.toFile).withFallback(ConfigFactory.load())
  val config: Config =
    if isProd then ConfigFactory.load("application-prod.conf")
    else localConfig
