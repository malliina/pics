import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import scala.sys.process.Process
import scala.util.Try

val webAuthVersion = "6.2.3"
val primitivesVersion = "3.2.0"
val munitVersion = "0.7.29"

val isProd = settingKey[Boolean]("isProd")

inThisBuild(
  Seq(
    organization := "com.malliina",
    version := "0.0.1",
    scalaVersion := "3.1.1",
    assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.rename
      case PathList("META-INF", "versions", xs @ _*) => MergeStrategy.rename
      case PathList("com", "malliina", xs @ _*)         => MergeStrategy.first
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
)

val commonSettings = Seq(
  libraryDependencies ++=
    Seq("generic", "parser").map(m => "io.circe" %%% s"circe-$m" % "0.14.2") ++ Seq(
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
    Compile / npmDependencies ++= Seq(
      "@popperjs/core" -> "2.11.0",
      "bootstrap" -> "5.1.3"
    ),
    Compile / npmDevDependencies ++= Seq(
      "autoprefixer" -> "10.4.1",
      "cssnano" -> "5.0.14",
      "css-loader" -> "6.5.1",
      "less" -> "4.1.2",
      "less-loader" -> "10.2.0",
      "mini-css-extract-plugin" -> "2.4.5",
      "postcss" -> "8.4.5",
      "postcss-import" -> "14.0.2",
      "postcss-loader" -> "6.2.1",
      "postcss-preset-env" -> "7.2.0",
      "style-loader" -> "3.3.1",
      "webpack-merge" -> "5.8.0"
    ),
    isProd := (Global / scalaJSStage).value == FullOptStage
  )

val backend = project
  .in(file("backend"))
  .enablePlugins(
    FileTreePlugin,
    BuildInfoPlugin,
    ServerPlugin,
    LiveRevolverPlugin
  )
  .dependsOn(crossJvm)
  .settings(commonSettings)
  .settings(
    clientProject := frontend,
    buildInfoPackage := "com.malliina.pics",
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      "gitHash" -> gitHash,
      "assetsDir" -> (frontend / assetsRoot).value,
      "publicFolder" -> (frontend / assetsPrefix).value,
      "mode" -> (if ((frontend / isProd).value) "prod" else "dev"),
      "isProd" -> (frontend / isProd).value
    ),
    libraryDependencies ++= Seq("ember-server", "dsl", "circe").map { m =>
      "org.http4s" %% s"http4s-$m" % "0.23.16"
    } ++ Seq("core", "hikari").map { d =>
      "org.tpolecat" %% s"doobie-$d" % "1.0.0-RC2"
    } ++ Seq("classic", "core").map { m =>
      "ch.qos.logback" % s"logback-$m" % "1.2.11"
    } ++ Seq(
      "com.typesafe" % "config" % "1.4.2",
      "org.apache.commons" % "commons-text" % "1.9",
      "software.amazon.awssdk" % "s3" % "2.17.243",
      "org.flywaydb" % "flyway-core" % "7.15.0",
      "mysql" % "mysql-connector-java" % "5.1.49",
      "com.sksamuel.scrimage" % "scrimage-core" % "4.0.31",
      "com.malliina" %% "logstreams-client" % "2.2.1",
      "com.malliina" %% "web-auth" % webAuthVersion,
      "org.slf4j" % "slf4j-api" % "1.7.36",
      "org.scalameta" %% "munit" % munitVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-mysql" % "0.40.10" % Test,
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    Compile / start := Def.taskIf {
      if (start.inputFileChanges.hasChanges) {
        refreshBrowsers.value
      } else {
        Def.task(streams.value.log.info("No backend changes."))
      }
    }.dependsOn(start).value,
    (frontend / Compile / start) := Def.taskIf {
      if ((frontend / Compile / start).inputFileChanges.hasChanges) {
        refreshBrowsers.value
      } else {
        Def.task(streams.value.log.info("No frontend changes.")).value
      }
    }.dependsOn(frontend / start).value,
    Compile / unmanagedResourceDirectories ++= {
      val prodAssets =
        if ((frontend / isProd).value) List((frontend / Compile / assetsRoot).value.getParent.toFile)
        else Nil
      (baseDirectory.value / "public") +: prodAssets
    },
    assembly / assemblyJarName := "app.jar"
  )

val pics = project
  .in(file("."))
  .aggregate(frontend, backend)

def gitHash: String =
  sys.env
    .get("GITHUB_SHA")
    .orElse(Try(Process("git rev-parse HEAD").lineStream.head).toOption)
    .getOrElse("unknown")

Global / onChangedBuildSource := ReloadOnSourceChanges
