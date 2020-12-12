import WebPlugin.makeAssetsFile
import com.typesafe.sbt.packager.docker.DockerVersion
import org.scalajs.sbtplugin.Stage
import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}

import scala.sys.process.Process
import scala.util.Try

val utilPlayVersion = "5.13.0"
val primitivesVersion = "1.18.0"
val munitVersion = "0.7.19"
val scalatagsVersion = "0.9.2"
val awsSdk2Version = "2.15.45"
val testContainersScalaVersion = "0.38.7"
val utilPlayDep = "com.malliina" %% "web-auth" % utilPlayVersion

inThisBuild(
  Seq(
    organization := "com.malliina",
    version := "0.0.1",
    scalaVersion := "2.13.3"
  )
)

val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
    "com.typesafe.play" %%% "play-json" % "2.9.1",
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
  .enablePlugins(ScalaJSBundlerPlugin, NodeJsPlugin, WebPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "1.1.0",
      "be.doeraene" %%% "scalajs-jquery" % "1.0.0",
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    version in webpack := "4.44.2",
//    version in webpack := "5.8.0",
    webpackEmitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    npmDependencies in Compile ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.14.0",
      "bootstrap" -> "4.5.2",
      "jquery" -> "3.5.1",
      "popper.js" -> "1.16.1"
    ),
    npmDevDependencies in Compile ++= Seq(
      "autoprefixer" -> "10.0.0",
      "cssnano" -> "4.1.10",
      "css-loader" -> "5.0.1",
      "file-loader" -> "6.1.0",
      "less" -> "3.12.2",
      "less-loader" -> "7.1.0",
      "mini-css-extract-plugin" -> "1.3.1",
      "postcss" -> "8.1.10",
      "postcss-import" -> "13.0.0",
      "postcss-loader" -> "4.1.0",
      "postcss-preset-env" -> "6.7.0",
      "style-loader" -> "2.0.0",
      "url-loader" -> "4.1.0",
      "webpack-merge" -> "5.1.4"
    ),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.dev.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.prod.config.js"),
    webpackBundlingMode in (Compile, fastOptJS) := BundlingMode.LibraryOnly(),
    webpackBundlingMode in (Compile, fullOptJS) := BundlingMode.Application
  )

val prodPort = 9000
val http4sModules = Seq("blaze-server", "blaze-client", "dsl", "scalatags", "play-json")

val backend = project
  .in(file("backend"))
  .enablePlugins(
    FileTreePlugin,
    JavaServerAppPackaging,
    SystemdPlugin,
    BuildInfoPlugin
  )
  .dependsOn(crossJvm)
  .settings(commonSettings)
  .settings(
    buildInfoPackage := "com.malliina.pics",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "hash" -> gitHash),
    libraryDependencies ++= http4sModules.map { m =>
      "org.http4s" %% s"http4s-$m" % "0.21.14"
    } ++ Seq("doobie-core", "doobie-hikari").map { d =>
      "org.tpolecat" %% d % "0.9.4"
    } ++ Seq(
      "com.github.pureconfig" %% "pureconfig" % "0.14.0",
      "org.apache.commons" % "commons-text" % "1.9",
      "software.amazon.awssdk" % "s3" % awsSdk2Version,
      "org.flywaydb" % "flyway-core" % "7.3.1",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "com.sksamuel.scrimage" % "scrimage-core" % "4.0.6",
      "com.malliina" %% "logstreams-client" % "1.10.1",
      utilPlayDep,
      "org.slf4j" % "slf4j-api" % "1.7.30",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "ch.qos.logback" % "logback-core" % "1.2.3",
      "org.scalameta" %% "munit" % munitVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-mysql" % testContainersScalaVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    name in Linux := "pics",
    packageName in Linux := (name in Linux).value,
    httpPort in Linux := Option(s"$prodPort"),
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
    unmanagedResourceDirectories in Compile += baseDirectory.value / "public",
    httpPort in Linux := Option(s"$prodPort"),
    dockerVersion := Option(DockerVersion(19, 3, 5, None)),
    dockerBaseImage := "openjdk:11",
    daemonUser in Docker := "pics",
    version in Docker := gitHash,
    dockerRepository := Option("malliinapics.azurecr.io"),
    dockerExposedPorts ++= Seq(prodPort),
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in packageDoc := false,
    sources in (Compile, doc) := Seq.empty,
    packageName in Docker := "pics",
    resources in Compile ++= Def.taskDyn {
      val sjsStage = scalaJSStage.in(frontend).value match {
        case Stage.FastOpt => fastOptJS
        case Stage.FullOpt => fullOptJS
      }
      Def.task {
        val webpackFiles = webpack.in(frontend, Compile, sjsStage).value.map(_.data)
        val hashedFiles = hashAssets.in(frontend, Compile, sjsStage).value.map(_.hashedFile.toFile)
        webpackFiles ++ hashedFiles
      }
    }.value,
    resourceDirectories in Compile += assetsDir.in(frontend).value.toFile,
    reStart := reStart.dependsOn(webpack.in(frontend, Compile, fastOptJS)).evaluated,
    watchSources ++= (watchSources in frontend).value,
    sourceGenerators in Compile += Def.taskDyn {
      val sjsStage = scalaJSStage.in(frontend).value match {
        case Stage.FastOpt => fastOptJS
        case Stage.FullOpt => fullOptJS
      }
      Def.task {
        val dest = (sourceManaged in Compile).value
        val hashed = hashAssets.in(frontend, Compile, sjsStage).value
        val log = streams.value.log
        val cached = FileFunction.cached(streams.value.cacheDirectory / "assets") { in =>
          makeAssetsFile(dest, hashed, log)
        }
        cached(hashed.map(_.hashedFile.toFile).toSet).toSeq
      }
    }.taskValue
  )

val runApp = inputKey[Unit]("Runs the app")

val pics = project
  .in(file("."))
  .aggregate(frontend, backend)
  .settings(commonSettings)
  .settings(
    runApp := (run in Compile).in(backend).evaluated,
    reStart := reStart.in(backend).evaluated
  )

def gitHash: String =
  sys.env
    .get("GITHUB_SHA")
    .orElse(Try(Process("git rev-parse --short HEAD").lineStream.head).toOption)
    .getOrElse("unknown")

Global / onChangedBuildSource := ReloadOnSourceChanges
