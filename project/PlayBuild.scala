import com.malliina.sbt.GenericKeys.manufacturer
import com.malliina.sbt.unix.LinuxKeys.{httpPort, httpsPort}
import com.malliina.sbt.unix.LinuxPlugin
import com.malliina.sbtplay.PlayProject
import com.typesafe.sbt.packager.{Keys => PackagerKeys}
import com.typesafe.sbt.SbtNativePackager._
import play.sbt.PlayImport
import sbt.Keys._
import sbt._

object PlayBuild {
  lazy val p = PlayProject.default("pics").settings(picsSettings: _*)

  val utilPlayDep = "com.malliina" %% "util-play" % "3.5.3"

  lazy val picsSettings = linuxSettings ++ Seq(
    organization := "com.malliina",
    version := "0.1.0",
    scalaVersion := "2.11.8",
    resolvers ++= Seq(
      Resolver.jcenterRepo,
      Resolver.bintrayRepo("malliina", "maven")
    ),
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk" % "1.11.75",
      PlayImport.cache,
      PlayImport.ws,
      utilPlayDep,
      utilPlayDep % Test classifier "tests"
    )
  )

  def linuxSettings = LinuxPlugin.playSettings ++ Seq(
    httpPort in Linux := Option("disabled"),
    httpsPort in Linux := Option("8459"),
    PackagerKeys.maintainer := "Michael Skogberg <malliina123@gmail.com>",
    manufacturer := "Skogberg Labs",
    javaOptions in Universal ++= {
      val linuxName = (name in Linux).value
      Seq(
        s"-Dgoogle.oauth=/etc/$linuxName/google-oauth.key",
        "-Dfile.encoding=UTF-8",
        "-Dsun.jnu.encoding=UTF-8"
      )
    },
    PackagerKeys.packageSummary in Linux := "This is the pics summary.",
    PackagerKeys.rpmVendor := "Skogberg Labs"
  )
}
