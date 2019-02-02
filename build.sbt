import com.malliina.sbt.filetree.DirMap
import play.sbt.PlayImport
import sbt.Keys.scalaVersion
import sbtcrossproject.CrossPlugin.autoImport.{
  CrossType => PortableType,
  crossProject => portableProject
}

val utilPlayVersion = "5.0.0"
val utilPlayDep = "com.malliina" %% "util-play" % utilPlayVersion

val commonSettings = Seq(
  organization := "com.malliina",
  scalaVersion := "2.12.8",
  resolvers ++= Seq(
    "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/",
    Resolver.bintrayRepo("malliina", "maven")
  ),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % "0.6.7",
    "com.typesafe.play" %%% "play-json" % "2.7.1",
    "com.malliina" %%% "primitives" % "1.8.1",
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
  .enablePlugins(ScalaJSBundlerPlugin, ScalaJSWeb)
  .dependsOn(crossJs)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.2",
      "org.scalatest" %%% "scalatest" % "3.0.5" % Test,
      "be.doeraene" %%% "scalajs-jquery" % "0.9.2"
    ),
    version in webpack := "4.27.1",
    emitSourceMaps := false,
    scalaJSUseMainModuleInitializer := true,
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    npmDependencies in Compile ++= Seq(
      "jquery" -> "3.3.1",
      "popper.js" -> "1.14.6",
      "bootstrap" -> "4.2.1"
    )
  )

val backend = Project("pics", file("backend"))
  .enablePlugins(FileTreePlugin, WebScalaJSBundlerPlugin, PlayLinuxPlugin)
  .dependsOn(crossJvm)
  .settings(commonSettings)
  .settings(
    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline),
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-text" % "1.6",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.461",
      "software.amazon.awssdk" % "s3" % "2.1.3",
      PlayImport.ehcache,
      PlayImport.ws,
      "com.malliina" %% "play-social" % utilPlayVersion,
      utilPlayDep,
      utilPlayDep % Test classifier "tests",
      "com.typesafe.slick" %% "slick" % "3.2.3",
      "com.h2database" % "h2" % "1.4.197",
      "mysql" % "mysql-connector-java" % "5.1.47",
      "com.zaxxer" % "HikariCP" % "3.2.0",
      "com.sksamuel.scrimage" %% "scrimage-core" % "2.1.8",
      "com.malliina" %% "logstreams-client" % "1.4.0"
    ),
    pipelineStages := Seq(digest, gzip),
    // pipelineStages in Assets := Seq(digest, gzip)
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
    )
  )

val root = project
  .in(file("."))
  .aggregate(frontend, backend)
  .settings(commonSettings)
