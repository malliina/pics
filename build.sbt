import com.malliina.sbt.unix.LinuxKeys.{httpPort, httpsPort}
import com.malliina.sbtplay.PlayProject
import com.typesafe.sbt.packager.Keys.{maintainer, packageSummary, rpmVendor}
import play.sbt.PlayImport
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.buildInfoKeys

lazy val p = PlayProject.server("pics")

val utilPlayDep = "com.malliina" %% "util-play" % "3.6.9"

organization := "com.malliina"
scalaVersion := "2.11.8"
libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.119",
  PlayImport.cache,
  PlayImport.ws,
  utilPlayDep,
  utilPlayDep % Test classifier "tests"
)
buildInfoKeys := Seq[BuildInfoKey](
  name,
  version,
  "hash" -> Process("git rev-parse --short HEAD").lines.head
)
httpPort in Linux := Option("disabled")
httpsPort in Linux := Option("8459")
maintainer := "Michael Skogberg <malliina123@gmail.com>"
manufacturer := "Skogberg Labs"
javaOptions in Universal ++= {
  val linuxName = (name in Linux).value
  Seq(
    s"-Dgoogle.oauth=/etc/$linuxName/google-oauth.key"
  )
}
packageSummary in Linux := "This is the pics summary."
rpmVendor := "Skogberg Labs"
