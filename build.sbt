import com.malliina.sbtplay.PlayProject
import play.sbt.PlayImport

lazy val p = PlayProject.linux("pics")

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
