import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

val versions = new {
  val logstreams = "2.8.3"
  val munit = "1.1.0"
  val primitives = "3.7.7"
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
        "io.circe" %%% s"circe-$m" % "0.14.10"
      } ++ Seq(
        "org.typelevel" %%% "case-insensitive" % "1.4.2",
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
        "org.apache.commons" % "commons-text" % "1.13.0",
        "software.amazon.awssdk" % "s3" % "2.30.12",
        "mysql" % "mysql-connector-java" % "8.0.33",
        "com.sksamuel.scrimage" % "scrimage-core" % "4.3.0",
        "com.malliina" %% "logstreams-client" % versions.logstreams,
        "com.malliina" %% "config" % versions.primitives,
        "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test
      ),
    assembly / assemblyJarName := "app.jar",
    Compile / resourceDirectories += io.Path.userHome / ".pics",
    Linux / name := "pics"
  )

val pics = project
  .in(file("."))
  .aggregate(frontend, backend)

Global / onChangedBuildSource := ReloadOnSourceChanges
