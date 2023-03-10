import scala.sys.process.{Process, ProcessLogger}
import sbt._
import sbt.Keys._
import org.apache.ivy.util.ChecksumHelper
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{ModuleKind, fastLinkJS, fastLinkJSOutput, fullLinkJS, fullLinkJSOutput, scalaJSLinkerConfig, scalaJSStage, scalaJSUseMainModuleInitializer}
import org.scalajs.sbtplugin.Stage
import sbt.Def.spaceDelimited
import sbt.nio.Keys.fileInputs

import java.nio.charset.StandardCharsets
import java.nio.file.Path

object RollupPlugin extends AutoPlugin {
  override def requires: Plugins = ScalaJSPlugin
  val utf8 = StandardCharsets.UTF_8
  val sha1 = "sha1"

  object autoImport {
    val build = GeneratorKeys.build
    val prepareRollup = taskKey[Path]("Prepares rollup")
    val assetsRoot = GeneratorKeys.assetsRoot
    val assetsPrefix = settingKey[String]("I don't know what this is")
    val front = inputKey[Int]("Runs the input as a command in the frontend working directory")
  }
  import autoImport._

  override val projectSettings: Seq[Def.Setting[?]] =
    stageSettings(Stage.FastOpt) ++
      stageSettings(Stage.FullOpt) ++
      Seq(
        scalaJSUseMainModuleInitializer := true,
        scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
        assetsRoot := (target.value / "assets").toPath,
        assetsPrefix := "assets",
        build := {
          Def.settingDyn {
            val stageTask = scalaJSStage.value match {
              case Stage.FastOpt => fastLinkJS
              case Stage.FullOpt => fullLinkJS
            }
            stageTask / build
          }
        }.value,
        front := {
          val log = streams.value.log
          val args: Seq[String] = spaceDelimited("<arg>").parsed
          val stringified = args.mkString(" ")
          val cwd = baseDirectory.value
          process(args, cwd, log)
        }
      )

  private def stageSettings(stage: Stage): Seq[Def.Setting[?]] = {
    val stageTaskOutput = stage match {
      case Stage.FastOpt => fastLinkJSOutput
      case Stage.FullOpt => fullLinkJSOutput
    }
    val stageTask = stage match {
      case Stage.FastOpt => fastLinkJS
      case Stage.FullOpt => fullLinkJS
    }
    val isProd = stageTask == Stage.FullOpt
    Seq(
      stageTask / prepareRollup := {
        val log = streams.value.log
        val isProd = stage == Stage.FullOpt
        val jsDir = (Compile / stageTaskOutput).value
        val mainJs = jsDir.relativeTo(baseDirectory.value).get / "main.js"
        log.info(s"Built $mainJs with prod $isProd.")
        val rollup = (target.value / "scalajs.rollup.config.js").toPath
        makeRollupConfig(mainJs.toPath, assetsRoot.value, rollup, isProd, log)
        jsDir.toPath
      },
      stageTask / build / fileInputs ++=
        (Compile / sourceDirectories).value.map(f => f.toGlob / ** / "*.scala") ++
          (Compile / resourceDirectories).value.map(f => f.toGlob / ** / *) ++
          Seq(baseDirectory.value.toGlob / "*.ts") ++
          Seq(baseDirectory.value / "package.json").map(_.toGlob),
      stageTask / build := {
        val log = streams.value.log
        val cwd = baseDirectory.value
        val packageJson = cwd / "package.json"
        val cacheFile = target.value / "package.json.sha1"
        val checksum = computeChecksum(packageJson)
        if (cacheFile.exists() && IO.readLines(cacheFile, utf8).headOption.contains(checksum)) {
          npmRunBuild(cwd, log)
        } else {
          IO.write(cacheFile, checksum, utf8)
          npmInstall(cwd, log)
          npmRunBuild(cwd, log)
        }
      },
      stageTask / build := (stageTask / build).dependsOn(stageTask / prepareRollup).value,
      stageTask / build := Def.taskIf {
        val log = streams.value.log
        val hasChanges = build.inputFileChanges.hasChanges
        if (hasChanges) {
          (stageTask / build).value
        } else {
          Def.task(()).value
        }
      }.value
    )
  }

  def npmRunBuild(cwd: File, log: ProcessLogger) =
    process(Seq("npm", "run", "build"), cwd, log)

  def npmInstall(cwd: File, log: ProcessLogger) =
    process(Seq("npm", "install"), cwd, log)

  def process(commands: Seq[String], cwd: File, log: ProcessLogger) = {
    log.out(s"Running '${commands.mkString(" ")}' from '$cwd'...")
    Process(canonical(commands), cwd).run(log).exitValue()
  }

  def canonical(cmd: Seq[String]): Seq[String] = {
    val isWindows = sys.props("os.name").toLowerCase().contains("win")
    val cmdPrefix = if (isWindows) Seq("cmd", "/c") else Nil
    cmdPrefix ++ cmd
  }

  def computeChecksum(file: File) = ChecksumHelper.computeAsString(file, sha1)

  def makeRollupConfig(
    input: Path,
    outputDir: Path,
    rollup: Path,
    isProd: Boolean,
    log: Logger
  ): Path = {
    val isProdStr = if (isProd) "true" else "false"
    val content = s"""
      |// Generated at build time
      |export const production = $isProdStr
      |export const outputDir = "$outputDir"
      |export const scalajs = {
      |  input: { frontend: "$input" },
      |  output: {
      |    dir: "$outputDir",
      |    format: "iife",
      |    sourcemap: true,
      |    name: "version"
      |  }
      |}""".stripMargin.trim
    IO.write(rollup.toFile, content, utf8)
    log.info(s"Wrote $rollup.")
    rollup
  }
}
