import com.malliina.sbt.filetree.DirMap
import play.sbt.PlayImport
import sbt.Keys.scalaVersion
import sbt._
import sbtcrossproject.CrossPlugin.autoImport.{
  CrossType => PortableType,
  crossProject => portableProject
}

import scala.sys.process.Process
import scala.util.Try

val utilPlayVersion = "5.4.0"
val scalaTestVersion = "3.0.8"
val primitivesVersion = "1.13.0"
val utilPlayDep = "com.malliina" %% "util-play" % utilPlayVersion

val commonSettings = Seq(
  organization := "com.malliina",
  scalaVersion := "2.13.1",
  resolvers ++= Seq(
    Resolver.bintrayRepo("malliina", "maven")
  ),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % "0.7.0",
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
    version in webpack := "4.35.2",
    emitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    npmDependencies in Compile ++= Seq(
      "@fortawesome/fontawesome-free" -> "5.9.0",
      "bootstrap" -> "4.3.1",
      "jquery" -> "3.4.1",
      "popper.js" -> "1.15.0"
    ),
    npmDevDependencies in Compile ++= Seq(
      "autoprefixer" -> "9.6.0",
      "cssnano" -> "4.1.10",
      "css-loader" -> "3.0.0",
      "file-loader" -> "4.0.0",
      "less" -> "3.9.0",
      "less-loader" -> "5.0.0",
      "mini-css-extract-plugin" -> "0.7.0",
      "postcss-import" -> "12.0.1",
      "postcss-loader" -> "3.0.0",
      "postcss-preset-env" -> "6.6.0",
      "style-loader" -> "0.23.1",
      "url-loader" -> "2.0.1",
      "webpack-merge" -> "4.2.1"
    ),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.dev.config.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.prod.config.js")
  )

val backend = project
  .in(file("backend"))
  .enablePlugins(FileTreePlugin, WebScalaJSBundlerPlugin, PlayLinuxPlugin, BuildInfoPlugin)
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
      "ch.vorburger.mariaDB4j" % "mariaDB4j" % "2.4.0"
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
      .value
  )

val pics = project
  .in(file("."))
  .aggregate(frontend, backend)
  .settings(commonSettings)

def gitHash: String =
  Try(Process("git rev-parse --short HEAD").lineStream.head).toOption.getOrElse("unknown")
