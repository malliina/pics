package com.malliina.pics.app

import com.malliina.config.ConfigNode
import com.malliina.pics.BuildInfo

import java.nio.file.{Path, Paths}

object LocalConf:
  val appDir: Path = Paths.get(sys.props("user.home")).resolve(".pics")
  private val localConfFile: Path = appDir.resolve("pics.conf")
  val isProd: Boolean = BuildInfo.mode == "prod"
  private val localConfig: ConfigNode = ConfigNode.default(localConfFile)
  val config: ConfigNode =
    if isProd then ConfigNode.load("application-prod.conf")
    else localConfig
