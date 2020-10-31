//import com.malliina.sbt.unix.LinuxKeys.ciBuild
//import com.malliina.sbt.unix.LinuxPlugin
//import play.sbt.PlayScala
import sbt.AutoPlugin
import sbt.Keys.clean
import sbt._
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess, releaseStepTask}
import sbtrelease.ReleaseStateTransformations.{checkSnapshotDependencies, runTest}
import com.typesafe.sbt.packager.archetypes.JavaServerAppPackaging
import com.typesafe.sbt.packager.archetypes.systemloader.SystemdPlugin

object PlayLinuxPlugin extends AutoPlugin {
  override def requires = JavaServerAppPackaging && SystemdPlugin

  override def projectSettings = Seq(
//    releaseProcess := Seq[ReleaseStep](
//      releaseStepTask(clean in Compile),
//      checkSnapshotDependencies,
//      runTest,
//      releaseStepTask(ciBuild)
//    )
  )
}
