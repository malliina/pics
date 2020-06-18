import com.malliina.sbt.filetree.DirMap
import com.malliina.sbt.unix.LinuxKeys.ciBuild
import play.sbt.PlayImport
import com.typesafe.sbt.packager.docker.DockerVersion

import sbt.Keys.scalaVersion
import sbt._
import sbtcrossproject.CrossPlugin.autoImport.{
  CrossType => PortableType,
  crossProject => portableProject
}
import sbtrelease.ReleaseStateTransformations.checkSnapshotDependencies

import scala.sys.process.Process
import scala.util.Try

val utilPlayVersion = "5.11.0"
val primitivesVersion = "1.17.0"
val munitVersion = "0.7.7"
val scalatagsVersion = "0.9.1"
val awsSdkVersion = "1.11.784"
val awsSdk2Version = "2.13.26"
val testContainersScalaVersion = "0.37.0"
val utilPlayDep = "com.malliina" %% "util-play" % utilPlayVersion

val commonSettings = Seq(
  organization := "com.malliina",
  scalaVersion := "2.13.2",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
    "com.typesafe.play" %%% "play-json" % "2.9.0",
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
  .enablePlugins(ScalaJSBundlerPlugin, NodeJsPlugin)
  .dependsOn(crossJs)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "1.0.0",
      "be.doeraene" %%% "scalajs-jquery" % "1.0.0",
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    version in webpack := "4.43.0",
    webpackEmitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    npmDependencies in Compile ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.13.0",
      "bootstrap" -> "4.5.0",
      "jquery" -> "3.5.1",
      "popper.js" -> "1.16.1"
    ),
    npmDevDependencies in Compile ++= Seq(
      "autoprefixer" -> "9.8.0",
      "cssnano" -> "4.1.10",
      "css-loader" -> "3.5.3",
      "file-loader" -> "6.0.0",
      "less" -> "3.11.1",
      "less-loader" -> "6.1.0",
      "mini-css-extract-plugin" -> "0.9.0",
      "postcss-import" -> "12.0.1",
      "postcss-loader" -> "3.0.0",
      "postcss-preset-env" -> "6.7.0",
      "style-loader" -> "1.2.1",
      "url-loader" -> "4.1.0",
      "webpack-merge" -> "4.2.2"
    ),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.dev.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.prod.config.js")
  )

val prodPort = 9000

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
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "software.amazon.awssdk" % "s3" % awsSdk2Version,
      PlayImport.ehcache,
      PlayImport.ws,
      "com.malliina" %% "play-social" % utilPlayVersion,
      "io.getquill" %% "quill-jdbc" % "3.5.1",
//      "io.getquill" %% "quill-jasync-mysql" % "3.5.2-SNAPSHOT",
      "org.flywaydb" % "flyway-core" % "6.1.1",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "com.sksamuel.scrimage" % "scrimage-core" % "4.0.3",
      "com.malliina" %% "logstreams-client" % "1.10.1",
      utilPlayDep,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-mysql" % testContainersScalaVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    // pipelineStages in Assets := Seq(digest, gzip)
    name in Linux := "pics",
    packageName in Linux := (name in Linux).value,
    httpPort in Linux := Option("disabled"),
    httpsPort in Linux := Option("8459"),
    maintainer := "Michael Skogberg <malliina123@gmail.com>",
    javaOptions in Universal ++= {
      Seq(
        "-J-Xmx1024m",
        "-Dpidfile.path=/dev/null",
        s"-Dhttp.port=$prodPort",
        "-Dlogger.resource=logback-prod.xml"
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
    httpPort in Linux := Option(s"$prodPort"),
    dockerVersion := Option(DockerVersion(19, 3, 5, None)),
    dockerBaseImage := "openjdk:11",
    daemonUser in Docker := "pics",
    version in Docker := gitHash,
    dockerRepository := Option("malliinapics.azurecr.io"),
    dockerExposedPorts ++= Seq(prodPort),
    packageName in Docker := "pics"
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
