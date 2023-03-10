import sbt._
import sbt.Keys.{resourceDirectories, sourceDirectories}
import sbt.nio.Keys.fileInputs

object FileInputPlugin extends AutoPlugin {
  val start = StartKeys.start
  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    start / fileInputs ++=
      (Compile / sourceDirectories).value.map(_.toGlob / ** / "*.scala") ++
        (Compile / resourceDirectories).value.map(_.toGlob / **)
  )
}
