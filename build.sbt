import com.malliina.sbtplay.PlayProject
import play.sbt.PlayImport
import sbt.Keys.scalaVersion

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .aggregate(frontend, backend)

lazy val backend = PlayProject.linux("pics")
  .settings(backendSettings: _*)
  .dependsOn(crossJvm)

lazy val frontend = project.in(file("frontend"))
  .settings(frontendSettings: _*)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(crossJs)

lazy val cross = crossProject.in(file("shared"))
  .settings(commonSettings: _*)

lazy val crossJvm = cross.jvm
lazy val crossJs = cross.js

val utilPlayVersion = "4.5.1"

val utilPlayDep = "com.malliina" %% "util-play" % utilPlayVersion

val commonSettings = Seq(
  organization := "com.malliina",
  scalaVersion := "2.12.4",
  resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % "0.6.7",
    "com.typesafe.play" %%% "play-json" % "2.6.8",
    "com.malliina" %%% "primitives" % "1.3.2",
    "com.malliina" %%% "util-html" % utilPlayVersion
  )
)
val backendSettings = commonSettings ++ scalaJSSettings ++ Seq(
  libraryDependencies ++= Seq(
    "org.apache.commons" % "commons-text" % "1.1",
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.232",
    PlayImport.ehcache,
    PlayImport.ws,
    utilPlayDep,
    utilPlayDep % Test classifier "tests",
    "com.typesafe.slick" %% "slick" % "3.2.1",
    "com.h2database" % "h2" % "1.4.196",
    "com.nimbusds" % "nimbus-jose-jwt" % "5.1",
    "com.sksamuel.scrimage" %% "scrimage-core" % "2.1.8",
    "com.malliina" %% "logstreams-client" % "0.0.9"
  ),
  pipelineStages := Seq(digest, gzip),
  // pipelineStages in Assets := Seq(digest, gzip)
  httpPort in Linux := Option("disabled"),
  httpsPort in Linux := Option("8459"),
  maintainer := "Michael Skogberg <malliina123@gmail.com>",
  javaOptions in Universal += {
    val linuxName = (name in Linux).value
    s"-Dgoogle.oauth=/etc/$linuxName/google-oauth.key"
  },
  packageSummary in Linux := "This is the pics summary.",
  rpmVendor := "Skogberg Labs",
  unmanagedResourceDirectories in Compile += baseDirectory.value / "files",
  routesImport ++= Seq(
    "com.malliina.pics.Key",
    "com.malliina.pics.Keys.bindable"
  )
)

val frontendSettings = commonSettings ++ Seq(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.2",
    "org.scalatest" %%% "scalatest" % "3.0.4" % Test,
    "be.doeraene" %%% "scalajs-jquery" % "0.9.2"
  ),
  scalaJSUseMainModuleInitializer := true
)

def scalaJSSettings = Seq(
  scalaJSProjects := Seq(frontend),
  pipelineStages in Assets := Seq(scalaJSPipeline)
)
