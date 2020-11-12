import java.nio.file.{Files, Path, StandardCopyOption}

import com.malliina.sbt.filetree.DirMap
import com.malliina.sbt.unix.LinuxKeys.ciBuild
//import play.sbt.PlayImport
import com.typesafe.sbt.packager.docker.DockerVersion

import scala.sys.process.Process
import scala.util.Try
import sbtcrossproject.CrossPlugin.autoImport.{
  CrossType => PortableType,
  crossProject => portableProject
}

val assetsDir = settingKey[Path]("Webpack assets dir to serve in server")
val prepTarget = taskKey[Path]("Prep target dir")

val utilPlayVersion = "5.11.3-SNAPSHOT"
val primitivesVersion = "1.17.0"
val munitVersion = "0.7.12"
val scalatagsVersion = "0.9.1"
val awsSdk2Version = "2.14.21"
val testContainersScalaVersion = "0.38.3"
val utilPlayDep = "com.malliina" %% "util-play" % utilPlayVersion

inThisBuild(
  Seq(
    organization := "com.malliina",
    version := "0.0.1",
    scalaVersion := "2.13.3",
    assetsDir := (baseDirectory.value / "assets").toPath
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
  .enablePlugins(ScalaJSBundlerPlugin, NodeJsPlugin)
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
      "css-loader" -> "4.3.0",
      "file-loader" -> "6.1.0",
      "less" -> "3.12.2",
      "less-loader" -> "7.0.1",
      "mini-css-extract-plugin" -> "0.11.2",
      "postcss" -> "8.0.5",
      "postcss-import" -> "12.0.1",
      "postcss-loader" -> "4.0.2",
      "postcss-preset-env" -> "6.7.0",
      "style-loader" -> "1.2.1",
      "url-loader" -> "4.1.0",
      "webpack-merge" -> "5.1.4"
    ),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.dev.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.prod.config.js"),
    webpackBundlingMode in (Compile, fastOptJS) := BundlingMode.LibraryOnly(),
    webpackBundlingMode in (Compile, fullOptJS) := BundlingMode.Application,
    webpack.in(Compile, fastOptJS) := {
      val files = webpack.in(Compile, fastOptJS).value
      val log = streams.value.log
      files.map {
        file =>
          val relativeFile = file.data.relativeTo(crossTarget.in(Compile, npmUpdate).value).get
          val dest = assetsDir.value.resolve(relativeFile.toPath)
          Files.createDirectories(dest.getParent)
          val path = file.data.toPath
          Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING)
          val size = Files.size(path)
          log.debug(
            s"Copied ${file.data} of type ${file.metadata.get(BundlerFileTypeAttr)} to '$dest', $size bytes."
          )
          file.copy(dest.toFile)(file.metadata)
      }
    },
    webpack.in(Compile, fastOptJS) := webpack.in(Compile, fastOptJS).dependsOn(prepTarget).value,
    prepTarget := {
      Files.createDirectories(assetsDir.value)
    }
  )

val prodPort = 9000
val http4sModules = Seq("blaze-server", "blaze-client", "dsl", "scalatags", "play-json")

val backend = project
  .in(file("backend"))
  .enablePlugins(
    FileTreePlugin,
    PlayLinuxPlugin,
    BuildInfoPlugin
//    PlayLiveReloadPlugin
  )
  .dependsOn(crossJvm)
  .settings(commonSettings)
  .settings(
    buildInfoPackage := "com.malliina.pics",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, "hash" -> gitHash),
//    scalaJSProjects := Seq(frontend),
//    pipelineStages := Seq(digest, gzip),
    // pipelineStages in Assets := Seq(digest, gzip)
    //    pipelineStages in Assets := Seq(scalaJSPipeline),
    libraryDependencies ++= http4sModules.map { m =>
      "org.http4s" %% s"http4s-$m" % "0.21.7"
    } ++ Seq("doobie-core", "doobie-hikari").map { d =>
      "org.tpolecat" %% d % "0.9.2"
    } ++ Seq(
      "com.github.pureconfig" %% "pureconfig" % "0.14.0",
      "org.apache.commons" % "commons-text" % "1.9",
      "software.amazon.awssdk" % "s3" % awsSdk2Version,
//      PlayImport.ehcache,
//      PlayImport.ws,
      "com.malliina" %% "play-social" % utilPlayVersion,
      "org.flywaydb" % "flyway-core" % "6.5.6",
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
//    routesImport ++= Seq(
//      "com.malliina.pics.Key",
//      "com.malliina.pics.Keys.bindable"
//    ),
//    linuxPackageSymlinks := linuxPackageSymlinks.value.filterNot(_.link == "/usr/bin/starter"),
//    fileTreeSources := Seq(
//      DirMap(
//        source = (resourceDirectory in Assets).value,
//        destination = "com.malliina.pics.assets.AppAssets",
//        mapFunc = "com.malliina.pics.html.PicsHtml.at"
//      )
//    ),
    httpPort in Linux := Option(s"$prodPort"),
    dockerVersion := Option(DockerVersion(19, 3, 5, None)),
    dockerBaseImage := "openjdk:11",
    daemonUser in Docker := "pics",
    version in Docker := gitHash,
    dockerRepository := Option("malliinapics.azurecr.io"),
    dockerExposedPorts ++= Seq(prodPort),
    packageName in Docker := "pics",
//    mainClass in reStart := Option("com.malliina.pics.http4s.PicsServer"),
    resources in Compile ++= webpack.in(frontend, Compile, fastOptJS).value.map(_.data),
    resourceDirectories in Compile += assetsDir.value.toFile,
    reStart := reStart.dependsOn(webpack.in(frontend, Compile, fastOptJS)).evaluated,
    watchSources ++= (watchSources in frontend).value
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
