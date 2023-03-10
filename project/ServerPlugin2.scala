import GeneratorKeys.build
import HashPlugin.autoImport.hash
import com.malliina.live.LiveReloadPlugin.autoImport.refreshBrowsers
import com.malliina.live.LiveRevolverPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.{fastLinkJS, fullLinkJS, scalaJSStage}
import org.scalajs.sbtplugin.Stage
import sbt.Keys._
import sbt._
import spray.revolver.GlobalState
import spray.revolver.RevolverPlugin.autoImport.reStart

object ServerPlugin2 extends AutoPlugin {
  override def requires: Plugins = LiveRevolverPlugin && FileInputPlugin && HashPlugin
  object autoImport {
    val start = StartKeys.start
    val clientProject = settingKey[Project]("Scala.js project")
  }
  import autoImport._

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    start := Def.taskIf {
      val log = streams.value.log
      val changes = start.inputFileChanges
      // Restarts if a) not running, or b) input files have changed
      val isRunning = GlobalState.get().getProcess(thisProjectRef.value).isDefined
      val word = if (isRunning) "" else "not "
      val fileWord = if (changes.hasChanges) "" else "not"
      log.debug(s"${name.value} ${word}running. Files ${fileWord}changed.")
      if (changes.hasChanges || !isRunning) {
        reStart.toTask(" ").dependsOn(hash).value
      } else {
        Def.task(streams.value.log.info(s"No changes to ${name.value}, no restart.")).value
      }
    }.value,
    start := Def.taskIf {
      if (start.inputFileChanges.hasChanges) {
        refreshBrowsers.value
      } else {
        Def.task(streams.value.log.info("No backend changes.")).value
      }
    }.dependsOn(start).value,
    watchSources := watchSources.value ++ Def.taskDyn(clientProject.value / watchSources).value,
    hash := hash
      .dependsOn(Def.taskDyn {
        val jsTask = (clientProject / scalaJSStage).value match {
          case Stage.FastOpt => fastLinkJS
          case Stage.FullOpt => fullLinkJS
        }
        clientProject.value / Compile / jsTask / build
      })
      .value,
    Compile / compile := (Compile / compile).dependsOn(hash).value,
    Compile / sourceGenerators += hash.map(_.map(_.toFile))
  )
}
