import sbt._
import sbt.Keys._

object BuildBuild {
  val settings = sbtPlugins ++ Seq(
    scalaVersion := "2.10.6",
    resolvers ++= Seq(
      Resolver.bintrayRepo("malliina", "maven"),
      Resolver.url("malliina bintray sbt", url("https://dl.bintray.com/malliina/sbt-plugins/"))(Resolver.ivyStylePatterns)
    ),
    scalacOptions ++= Seq("-unchecked", "-deprecation")
  )

  def sbtPlugins = Seq(
    "com.malliina" % "sbt-play" % "0.9.2",
    "com.malliina" %% "sbt-packager" % "2.1.0"
  ) map addSbtPlugin
}
