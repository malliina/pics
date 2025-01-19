package com.malliina.pics.app

import com.malliina.config.ConfigNode
import com.malliina.pics.BuildInfo

import java.nio.file.{Path, Paths}

object LocalConf:
  private val appDir: Path = Paths.get(sys.props("user.home")).resolve(".pics")
  val isProd: Boolean = BuildInfo.mode == "prod"
  def local(file: String) = ConfigNode.default(appDir.resolve(file))
  private val localConfig: ConfigNode = local("pics.conf")
  val config: ConfigNode =
    if isProd then ConfigNode.load("application-prod.conf")
    else localConfig
