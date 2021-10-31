import com.typesafe.sbt.packager.docker.DockerVersion
import org.scalajs.sbtplugin.Stage
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}

import scala.sys.process.Process
import scala.util.Try

val webAuthVersion = "6.0.4"
val primitivesVersion = "3.0.2"
val munitVersion = "0.7.29"
val scalatagsVersion = "0.9.4"

inThisBuild(
  Seq(
    organization := "com.malliina",
    version := "0.0.1",
    scalaVersion := "3.1.0"
  )
)

val circeModules = Seq("generic", "parser")
val commonSettings = Seq(
  libraryDependencies ++=
    circeModules.map(m => "io.circe" %%% s"circe-$m" % "0.14.1") ++ Seq(
      "com.malliina" %%% "primitives" % primitivesVersion,
      "com.malliina" %%% "util-html" % webAuthVersion
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
  .enablePlugins(NodeJsPlugin, ClientPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(commonSettings)
  .settings(
    assetsPackage := "com.malliina.pics.assets",
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    webpack / version := "4.44.2",
    webpackEmitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    Compile / npmDependencies ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.15.4",
      "@popperjs/core" -> "2.10.2",
      "bootstrap" -> "5.1.3"
    ),
    Compile / npmDevDependencies ++= Seq(
      "autoprefixer" -> "10.2.5",
      "cssnano" -> "4.1.11",
      "css-loader" -> "5.2.1",
      "file-loader" -> "6.2.0",
      "less" -> "4.1.1",
      "less-loader" -> "7.3.0",
      "mini-css-extract-plugin" -> "1.4.1",
      "postcss" -> "8.2.9",
      "postcss-import" -> "14.0.1",
      "postcss-loader" -> "4.2.0",
      "postcss-preset-env" -> "6.7.0",
      "style-loader" -> "2.0.0",
      "url-loader" -> "4.1.1",
      "webpack-merge" -> "5.7.3"
    ),
    fastOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.dev.config.js"),
    fullOptJS / webpackConfigFile := Some(baseDirectory.value / "webpack.prod.config.js"),
    Compile / fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
    Compile / fullOptJS / webpackBundlingMode := BundlingMode.Application
  )

val prodPort = 9000
val http4sModules = Seq("blaze-server", "blaze-client", "dsl", "circe")

val backend = project
  .in(file("backend"))
  .enablePlugins(
    FileTreePlugin,
    JavaServerAppPackaging,
    SystemdPlugin,
    BuildInfoPlugin,
    ServerPlugin
  )
  .dependsOn(crossJvm)
  .settings(commonSettings)
  .settings(
    clientProject := frontend,
    buildInfoPackage := "com.malliina.pics",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "hash" -> gitHash),
    libraryDependencies ++= http4sModules.map { m =>
      "org.http4s" %% s"http4s-$m" % "0.23.6"
    } ++ Seq("doobie-core", "doobie-hikari").map { d =>
      "org.tpolecat" %% d % "1.0.0-RC1"
    } ++ Seq("classic", "core").map { m =>
      "ch.qos.logback" % s"logback-$m" % "1.2.6"
    } ++ Seq(
      "com.typesafe" % "config" % "1.4.1",
      "org.apache.commons" % "commons-text" % "1.9",
      "software.amazon.awssdk" % "s3" % "2.17.70",
      "org.flywaydb" % "flyway-core" % "7.15.0",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "com.sksamuel.scrimage" % "scrimage-core" % "4.0.22",
      "com.malliina" %% "logstreams-client" % "2.0.2",
      "com.malliina" %% "web-auth" % webAuthVersion,
      "org.slf4j" % "slf4j-api" % "1.7.32",
      "org.scalameta" %% "munit" % munitVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-mysql" % "0.39.10" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    Linux / name := "pics",
    Linux / packageName := (Linux / name).value,
    Linux / httpPort := Option(s"$prodPort"),
    maintainer := "Michael Skogberg <malliina123@gmail.com>",
    Universal / javaOptions ++= {
      Seq(
        "-J-Xmx1024m",
        s"-Dhttp.port=$prodPort",
        "-Dlogback.configurationFile=logback-prod.xml"
      )
    },
    Linux / packageSummary := "This is the pics summary.",
    rpmVendor := "Skogberg Labs",
    Compile / unmanagedResourceDirectories += baseDirectory.value / "public",
    Linux / httpPort := Option(s"$prodPort"),
    dockerVersion := Option(DockerVersion(19, 3, 5, None)),
    dockerBaseImage := "openjdk:11",
    Docker / daemonUser := "pics",
    Docker / version := gitHash,
    dockerRepository := Option("malliinacr.azurecr.io"),
    dockerExposedPorts ++= Seq(prodPort),
    Compile / packageDoc / publishArtifact := false,
    packageDoc / publishArtifact := false,
    Compile / doc / sources := Seq.empty,
    Docker / packageName := "pics"
  )

val runApp = inputKey[Unit]("Runs the app")

val pics = project
  .in(file("."))
  .aggregate(frontend, backend)
  .settings(commonSettings)
  .settings(
    start := (backend / start).value
  )

def gitHash: String =
  sys.env
    .get("GITHUB_SHA")
    .orElse(Try(Process("git rev-parse HEAD").lineStream.head).toOption)
    .getOrElse("unknown")

Global / onChangedBuildSource := ReloadOnSourceChanges
