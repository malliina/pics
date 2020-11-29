import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}

import sbt._
import sbt.Keys._
import org.scalajs.sbtplugin.Stage
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.apache.ivy.util.ChecksumHelper
import sbt.internal.util.ManagedLogger

case class HashedFile(path: String, hashedPath: String, originalFile: Path, hashedFile: Path)

object WebPlugin extends AutoPlugin {
  override def requires = ScalaJSBundlerPlugin
  object autoImport {
    val assetsDir = settingKey[Path]("Webpack assets dir to serve in server")
    val prepTarget = taskKey[Path]("Prep target dir")
    val hashAssets = taskKey[Seq[HashedFile]]("Hashed files")
  }
  import autoImport._
  override def projectSettings: Seq[Def.Setting[_]] =
    stageSettings(Stage.FastOpt) ++ stageSettings(Stage.FullOpt) ++ Seq(
      assetsDir := (baseDirectory.value / "target" / "assets").toPath,
      prepTarget := Files.createDirectories(assetsDir.value)
    )

  private def stageSettings(stage: Stage): Seq[Def.Setting[_]] = {
    val stageTask = stage match {
      case Stage.FastOpt => fastOptJS
      case Stage.FullOpt => fullOptJS
    }
    Seq(
      hashAssets.in(Compile, stageTask) := {
        val files = webpack.in(Compile, stageTask).value
        val log = streams.value.log
        files.flatMap { file =>
          val root = assetsDir.value
          val relativeFile = file.data.relativeTo(root.toFile).get
          val dest = file.data.toPath
          val extraFiles =
            if (!relativeFile.toPath.startsWith("static")) {
              val hashed = prepFile(dest, log)
              List(
                HashedFile(
                  root.relativize(dest).toString.replace('\\', '/'),
                  root.relativize(hashed).toString.replace('\\', '/'),
                  dest,
                  hashed
                )
              )
            } else {
              Nil
            }
          extraFiles
        }
      },
      webpack.in(Compile, stageTask) := {
        val files = webpack.in(Compile, stageTask).value
        val log = streams.value.log
        files.map { file =>
          val relativeFile = file.data.relativeTo(crossTarget.in(Compile, npmUpdate).value).get
          val dest = assetsDir.value.resolve(relativeFile.toPath)
          val path = file.data.toPath
          Files.createDirectories(dest.getParent)
          Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING)
          log.debug(s"Wrote '$dest', ${Files.size(path)} bytes.")
          Files.createDirectories(dest.getParent)
          file.copy(dest.toFile)(file.metadata)
        }
      },
      webpack.in(Compile, stageTask) := webpack.in(Compile, stageTask).dependsOn(prepTarget).value
    )
  }

  def prepFile(file: Path, log: ManagedLogger) = {
    val algorithm = "md5"
    val checksum = ChecksumHelper.computeAsString(file.toFile, algorithm)
    val checksumFile = file.getParent.resolve(s"${file.getFileName}.$algorithm")
    if (!Files.exists(checksumFile)) {
      Files.writeString(checksumFile, checksum)
      log.info(s"Wrote $checksumFile.")
    }
    val (base, ext) = file.toFile.baseAndExt
    val hashedFile = file.getParent.resolve(s"$base.$checksum.$ext")
    if (!Files.exists(hashedFile)) {
      Files.copy(file, hashedFile)
      log.info(s"Wrote $hashedFile.")
    }
    hashedFile
  }

  def makeAssetsFile(base: File, hashes: Seq[HashedFile], log: ManagedLogger): Set[File] = {
    val inlined = hashes.map(h => s""""${h.path}" -> "${h.hashedPath}"""").mkString(", ")
    val packageName = "com.malliina.pics.assets"
    val objectName = "HashedAssets"
    val content =
      s"""
       |package $packageName
       |
       |object $objectName { 
       |  val assets: Map[String, String] = Map($inlined)
       |}
       |""".stripMargin.trim + IO.Newline
    val destFile = destDir(base, packageName) / s"$objectName.scala"
    IO.write(destFile, content, StandardCharsets.UTF_8)
    log.info(s"Wrote $destFile.")
    Set(destFile)
  }

  def destDir(base: File, packageName: String): File =
    packageName.split('.').foldLeft(base)((acc, part) => acc / part)
}