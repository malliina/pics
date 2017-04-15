import com.malliina.sbt.GenericKeys.manufacturer
import com.malliina.sbt.unix.LinuxKeys.{httpPort, httpsPort}
import com.malliina.sbt.unix.LinuxPlugin
import com.malliina.sbtplay.PlayProject
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys.{maintainer, packageSummary, rpmVendor}
import play.sbt.PlayImport
import sbt.Keys._
import sbt._

object PlayBuild {
  lazy val p = PlayProject.server("pics")
    .settings(picsSettings: _*)

  val utilPlayDep = "com.malliina" %% "util-play" % "3.6.9"

  lazy val picsSettings = linuxSettings ++ Seq(
    organization := "com.malliina",
    version := "0.1.4",
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.119",
      PlayImport.cache,
      PlayImport.ws,
      utilPlayDep,
      utilPlayDep % Test classifier "tests"
    )
  )

  def linuxSettings = Seq(
    httpPort in Linux := Option("disabled"),
    httpsPort in Linux := Option("8459"),
    maintainer := "Michael Skogberg <malliina123@gmail.com>",
    manufacturer := "Skogberg Labs",
    javaOptions in Universal ++= {
      val linuxName = (name in Linux).value
      Seq(
        s"-Dgoogle.oauth=/etc/$linuxName/google-oauth.key"
      )
    },
    packageSummary in Linux := "This is the pics summary.",
    rpmVendor := "Skogberg Labs"
  )
}
