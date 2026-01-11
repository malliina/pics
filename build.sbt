import com.malliina.rollup.CommonKeys.isProd
import org.scalajs.sbtplugin.Stage
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

val versions = new {
  val ci = "1.4.2"
  val circe = "0.14.10"
  val commonsText = "1.15.0"
  val mariadb = "3.5.7"
  val munit = "1.2.1"
  val munitCats = "2.1.0"
  val s3 = "2.41.5"
  val scrimage = "4.3.5"
  val util = "6.11.1"
}

inThisBuild(
  Seq(
    organization := "com.malliina",
    version := "0.0.1",
    scalaVersion := "3.7.4",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % versions.munit % Test
    ),
    assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.rename
      case PathList("META-INF", "versions", xs @ _*)            => MergeStrategy.first
      case PathList("com", "malliina", xs @ _*)                 => MergeStrategy.first
      case PathList("META-INF", "okio.kotlin_module")           => MergeStrategy.first
      case PathList("module-info.class")                        => MergeStrategy.first
      case x                                                    =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    scalacOptions ++= Seq(
      "-Wunused:all"
    ),
    // https://users.scala-lang.org/t/scala-js-with-3-7-0-package-scala-contains-object-and-package-with-same-name-caps/10786/6
    dependencyOverrides += "org.scala-lang" %% "scala3-library" % scalaVersion.value
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
        "com.malliina" %%% "primitives" % versions.util,
        "com.malliina" %%% "util-html" % versions.util
      )
  )

val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(EsbuildPlugin, BuildInfoPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(
    buildInfoPackage := "com.malliina.pics",
    isProd := scalaJSStage.value == Stage.FullOpt,
    buildInfoKeys ++= Seq[BuildInfoKey](
      "isProd" -> isProd.value,
      "isProdLog" -> isProd.value
    )
  )

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
        "com.malliina" %% m % versions.util
      } ++ Seq(
        "org.apache.commons" % "commons-text" % versions.commonsText,
        "software.amazon.awssdk" % "s3" % versions.s3,
        "org.mariadb.jdbc" % "mariadb-java-client" % versions.mariadb,
        "com.sksamuel.scrimage" % "scrimage-core" % versions.scrimage,
        "com.malliina" %% "logstreams-client" % versions.util,
        "com.malliina" %% "config" % versions.util,
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
