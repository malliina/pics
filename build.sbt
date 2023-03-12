import sbtcrossproject.CrossPlugin.autoImport.{CrossType => PortableType, crossProject => portableProject}
import scala.sys.process.Process
import scala.util.Try

val webAuthVersion = "6.5.0"
val primitivesVersion = "3.4.0"
val munitVersion = "0.7.29"

val isProd = settingKey[Boolean]("isProd")

inThisBuild(
  Seq(
    organization := "com.malliina",
    version := "0.0.1",
    scalaVersion := "3.2.2",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.rename
      case PathList("META-INF", "versions", xs @ _*)            => MergeStrategy.first
      case PathList("com", "malliina", xs @ _*)                 => MergeStrategy.first
      case PathList("module-info.class")                        => MergeStrategy.first
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
)

val cross = portableProject(JSPlatform, JVMPlatform)
  .crossType(PortableType.Full)
  .in(file("shared"))
  .settings(
    libraryDependencies ++=
      Seq("generic", "parser").map { m =>
        "io.circe" %%% s"circe-$m" % "0.14.5"
      } ++ Seq(
        "com.malliina" %%% "primitives" % primitivesVersion,
        "com.malliina" %%% "util-html" % webAuthVersion
      )
  )

val crossJvm = cross.jvm
val crossJs = cross.js

val frontend = project
  .in(file("frontend"))
  .enablePlugins(NodeJsPlugin, RollupPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(crossJs)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % munitVersion % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    isProd := (Global / scalaJSStage).value == FullOptStage
  )

val backend = project
  .in(file("backend"))
  .enablePlugins(
    FileTreePlugin,
    BuildInfoPlugin,
    ServerPlugin
  )
  .dependsOn(crossJvm)
  .settings(
    clientProject := frontend,
    hashPackage := "com.malliina.pics.assets",
    hashRoot := Def.settingDyn { clientProject.value / assetsRoot }.value,
    buildInfoPackage := "com.malliina.pics",
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      "gitHash" -> gitHash,
      "assetsDir" -> (frontend / assetsRoot).value.toFile,
      "publicDir" -> (Compile / resourceDirectory).value.toPath.resolve("public"),
      "publicFolder" -> (frontend / assetsPrefix).value,
      "mode" -> (if ((frontend / isProd).value) "prod" else "dev"),
      "isProd" -> (frontend / isProd).value
    ),
    libraryDependencies ++= Seq("ember-server", "dsl", "circe").map { m =>
      "org.http4s" %% s"http4s-$m" % "0.23.18"
    } ++ Seq("core", "hikari").map { d =>
      "org.tpolecat" %% s"doobie-$d" % "1.0.0-RC2"
    } ++ Seq(
      "org.apache.commons" % "commons-text" % "1.10.0",
      "software.amazon.awssdk" % "s3" % "2.20.17",
      "org.flywaydb" % "flyway-core" % "7.15.0",
      "mysql" % "mysql-connector-java" % "8.0.32",
      "com.sksamuel.scrimage" % "scrimage-core" % "4.0.33",
      "com.malliina" %% "logstreams-client" % "2.5.0",
      "com.malliina" %% "web-auth" % webAuthVersion,
      "com.malliina" %% "config" % primitivesVersion,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-mysql" % "0.40.12" % Test,
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
//    (frontend / Compile / build) := Def.taskIf {
//      if ((frontend / Compile / build).inputFileChanges.hasChanges) {
//        refreshBrowsers.value
//      } else {
//        Def.task(streams.value.log.info("No frontend changes.")).value
//      }
//    }.dependsOn(frontend / Compile / start).value,
    copyFolders += ((Compile / resourceDirectory).value / "public").toPath,
    Compile / unmanagedResourceDirectories ++= {
      if ((frontend / isProd).value)
        List((frontend / Compile / assetsRoot).value.getParent.toFile)
      else Nil
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
