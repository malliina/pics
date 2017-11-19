import com.malliina.sbtplay.PlayProject
import play.sbt.PlayImport

lazy val p = PlayProject.linux("pics")

val utilPlayDep = "com.malliina" %% "util-play" % "4.4.0"

organization := "com.malliina"
scalaVersion := "2.12.4"
libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-text" % "1.1",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.232",
  PlayImport.ehcache,
  PlayImport.ws,
  utilPlayDep,
  utilPlayDep % Test classifier "tests"
)
pipelineStages := Seq(digest, gzip)
pipelineStages in Assets := Seq(digest, gzip)
httpPort in Linux := Option("disabled")
httpsPort in Linux := Option("8459")
maintainer := "Michael Skogberg <malliina123@gmail.com>"
javaOptions in Universal ++= {
  val linuxName = (name in Linux).value
  Seq(
    s"-Dgoogle.oauth=/etc/$linuxName/google-oauth.key"
  )
}
packageSummary in Linux := "This is the pics summary."
rpmVendor := "Skogberg Labs"
unmanagedResourceDirectories in Compile += baseDirectory.value / "files"
