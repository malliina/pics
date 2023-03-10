import sbt.{settingKey, taskKey}

import java.nio.file.Path

object GeneratorKeys {
  val build = taskKey[Unit]("Builds app") // Consider replacing with compile
  val isProd = settingKey[Boolean]("true if in prod mode, false otherwise")
  val assetsRoot = settingKey[Path]("Assets root directory")
}
