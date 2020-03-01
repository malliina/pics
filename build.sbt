import com.malliina.sbt.filetree.DirMap
import com.malliina.sbt.unix.LinuxKeys.ciBuild
import play.sbt.PlayImport
import sbt.Keys.scalaVersion
import sbt._
import sbtcrossproject.CrossPlugin.autoImport.{
  CrossType => PortableType,
  crossProject => portableProject
}
import sbtrelease.ReleaseStateTransformations.checkSnapshotDependencies

import scala.sys.process.Process
import scala.util.Try

val utilPlayVersion = "5.4.0"
val scalaTestVersion = "3.0.8"
val primitivesVersion = "1.13.0"
val testContainersScalaVersion = "0.35.2"

val utilPlayDep = "com.malliina" %% "util-play" % utilPlayVersion

val commonSettings = Seq(
  organization := "com.malliina",
  scalaVersion := "2.13.1",
  resolvers ++= Seq(
    Resolver.bintrayRepo("malliina", "maven")
  ),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % "0.8.5",
    "com.typesafe.play" %%% "play-json" % "2.8.1",
    "com.malliina" %%% "primitives" % primitivesVersion,
    "com.malliina" %%% "util-html" % utilPlayVersion
  )
)

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .settings(commonSettings: _*)

val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSBundlerPlugin, ScalaJSWeb, NodeJsPlugin)
  .dependsOn(crossJs)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.7",
      "be.doeraene" %%% "scalajs-jquery" % "0.9.5",
      "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
    ),
    version in webpack := "4.41.6",
    emitSourceMaps := false,
    webpackEmitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    npmDependencies in Compile ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.12.1",
      "bootstrap" -> "4.4.1",
      "jquery" -> "3.4.1",
      "popper.js" -> "1.16.1"
    ),
    npmDevDependencies in Compile ++= Seq(
      "autoprefixer" -> "9.7.4",
      "cssnano" -> "4.1.10",
      "css-loader" -> "3.4.2",
      "file-loader" -> "5.0.2",
      "less" -> "3.11.1",
      "less-loader" -> "5.0.0",
      "mini-css-extract-plugin" -> "0.9.0",
      "postcss-import" -> "12.0.1",
      "postcss-loader" -> "3.0.0",
      "postcss-preset-env" -> "6.7.0",
      "style-loader" -> "1.1.3",
      "url-loader" -> "3.0.0",
      "webpack-merge" -> "4.2.2"
    ),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.dev.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.prod.config.js")
  )

val backend = project
  .in(file("backend"))
  .enablePlugins(
    FileTreePlugin,
    WebScalaJSBundlerPlugin,
    PlayLinuxPlugin,
    BuildInfoPlugin,
    PlayLiveReloadPlugin
  )
  .dependsOn(crossJvm)
  .settings(commonSettings)
  .settings(
    buildInfoPackage := "com.malliina.pics",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "hash" -> gitHash),
    scalaJSProjects := Seq(frontend),
    pipelineStages := Seq(digest, gzip),
    pipelineStages in Assets := Seq(scalaJSPipeline),
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-text" % "1.8",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.693",
      "software.amazon.awssdk" % "s3" % "2.10.35",
      PlayImport.ehcache,
      PlayImport.ws,
      "com.malliina" %% "play-social" % utilPlayVersion,
      "io.getquill" %% "quill-jdbc" % "3.5.0",
      "org.flywaydb" % "flyway-core" % "6.1.1",
      "mysql" % "mysql-connector-java" % "5.1.48",
      "com.malliina" %% "scrimage-core" % "2.1.10",
      "com.malliina" %% "logstreams-client" % "1.8.1",
      utilPlayDep,
      utilPlayDep % Test classifier "tests",
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
      "com.dimafeng" %% "testcontainers-scala-scalatest" % testContainersScalaVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-mysql" % testContainersScalaVersion % Test
    ),
    // pipelineStages in Assets := Seq(digest, gzip)
    name in Linux := "pics",
    packageName in Linux := (name in Linux).value,
    httpPort in Linux := Option("disabled"),
    httpsPort in Linux := Option("8459"),
    maintainer := "Michael Skogberg <malliina123@gmail.com>",
    javaOptions in Universal ++= {
      val linuxName = (name in Linux).value
      Seq(
        s"-Dconfig.file=/etc/$linuxName/production.conf",
        s"-Dlogger.file=/etc/$linuxName/logback-prod.xml"
      )
    },
    packageSummary in Linux := "This is the pics summary.",
    rpmVendor := "Skogberg Labs",
    unmanagedResourceDirectories in Compile += baseDirectory.value / "files",
    routesImport ++= Seq(
      "com.malliina.pics.Key",
      "com.malliina.pics.Keys.bindable"
    ),
    linuxPackageSymlinks := linuxPackageSymlinks.value.filterNot(_.link == "/usr/bin/starter"),
    fileTreeSources := Seq(
      DirMap(
        source = (resourceDirectory in Assets).value,
        destination = "com.malliina.pics.assets.AppAssets",
        mapFunc = "com.malliina.pics.html.PicsHtml.at"
      )
    ),
    // Exposes as sbt-web assets some files retrieved from the NPM packages of the `client` project
    npmAssets ++= NpmAssets
      .ofProject(frontend) { modules =>
        (modules / "bootstrap").allPaths +++ (modules / "@fortawesome" / "fontawesome-free").allPaths
      }
      .value,
    releaseProcess := Seq[ReleaseStep](
      releaseStepTask(clean in Compile),
      checkSnapshotDependencies,
      releaseStepInputTask(testOnly, " * -- -l tests.DbTest"),
      releaseStepTask(ciBuild)
    )
  )

val runApp = inputKey[Unit]("Runs the app")

val pics = project
  .in(file("."))
  .aggregate(frontend, backend)
  .settings(commonSettings)
  .settings(
    runApp := (run in Compile).in(backend).evaluated
  )

def gitHash: String =
  Try(Process("git rev-parse --short HEAD").lineStream.head).toOption.getOrElse("unknown")

Global / onChangedBuildSource := ReloadOnSourceChanges
