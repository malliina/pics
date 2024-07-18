import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}

val webAuthVersion = "6.9.1"
val primitivesVersion = "3.7.1"
val munitVersion = "1.0.0"

inThisBuild(
  Seq(
    organization := "com.malliina",
    version := "0.0.1",
    scalaVersion := "3.4.0",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
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

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .settings(
    libraryDependencies ++=
      Seq("generic", "parser").map { m =>
        "io.circe" %%% s"circe-$m" % "0.14.9"
      } ++ Seq(
        "org.typelevel" %%% "case-insensitive" % "1.4.0",
        "com.malliina" %%% "primitives" % primitivesVersion,
        "com.malliina" %%% "util-html" % webAuthVersion
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
        "com.malliina" %% m % webAuthVersion
      } ++ Seq(
        "org.apache.commons" % "commons-text" % "1.11.0",
        "software.amazon.awssdk" % "s3" % "2.25.27",
        "mysql" % "mysql-connector-java" % "8.0.33",
        "com.sksamuel.scrimage" % "scrimage-core" % "4.1.3",
        "com.malliina" %% "logstreams-client" % "2.8.0",
        "com.malliina" %% "config" % primitivesVersion,
        "com.dimafeng" %% "testcontainers-scala-mysql" % "0.41.4" % Test,
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
