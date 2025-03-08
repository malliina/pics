import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

val versions = new {
  val ci = "1.4.2"
  val circe = "0.14.10"
  val commonsText = "1.13.0"
  val logstreams = "2.8.3"
  val mysql = "8.0.33"
  val munit = "1.1.0"
  val munitCats = "2.0.0"
  val primitives = "3.7.7"
  val s3 = "2.30.36"
  val scrimage = "4.3.0"
  val webAuth = "6.9.8"
}

inThisBuild(
  Seq(
    organization := "com.malliina",
    version := "0.0.1",
    scalaVersion := "3.6.2",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % versions.munit % Test
    ),
    assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.rename
      case PathList("META-INF", "versions", xs @ _*)            => MergeStrategy.first
      case PathList("com", "malliina", xs @ _*)                 => MergeStrategy.first
      case PathList("META-INF", "okio.kotlin_module")           => MergeStrategy.first
      case PathList("module-info.class")                        => MergeStrategy.first
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    scalacOptions ++= Seq(
      "-Wunused:all"
    )
  )
)

val cross = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("shared"))
  .settings(
    libraryDependencies ++=
      Seq("generic", "parser").map { m =>
        "io.circe" %%% s"circe-$m" % versions.circe
      } ++ Seq(
        "org.typelevel" %%% "case-insensitive" % versions.ci,
        "com.malliina" %%% "primitives" % versions.primitives,
        "com.malliina" %%% "util-html" % versions.webAuth
      )
  )

val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(RollupPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)

val backend = project
  .in(file("backend"))
  .enablePlugins(ServerPlugin, DebPlugin)
  .dependsOn(crossJvm)
  .settings(
    clientProject := frontend,
    dependentModule := crossJvm,
    hashPackage := "com.malliina.pics.assets",
    buildInfoPackage := "com.malliina.pics",
    libraryDependencies ++=
      Seq("util-http4s", "web-auth", "database").map { m =>
        "com.malliina" %% m % versions.webAuth
      } ++ Seq(
        "org.apache.commons" % "commons-text" % versions.commonsText,
        "software.amazon.awssdk" % "s3" % versions.s3,
        "mysql" % "mysql-connector-java" % versions.mysql,
        "com.sksamuel.scrimage" % "scrimage-core" % versions.scrimage,
        "com.malliina" %% "logstreams-client" % versions.logstreams,
        "com.malliina" %% "config" % versions.primitives,
        "org.typelevel" %% "munit-cats-effect" % versions.munitCats % Test
      ),
    assembly / assemblyJarName := "app.jar",
    Compile / resourceDirectories += io.Path.userHome / ".pics",
    Linux / name := "pics"
  )

val pics = project
  .in(file("."))
  .aggregate(frontend, backend)

Global / onChangedBuildSource := ReloadOnSourceChanges
