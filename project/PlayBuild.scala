import com.malliina.sbtplay.PlayProject
import play.sbt.PlayImport
import sbt.Keys._
import sbt._

object PlayBuild {
  lazy val p = PlayProject.default("pics").settings(commonSettings: _*)

  val utilPlayDep = "com.malliina" %% "util-play" % "3.3.3"

  lazy val commonSettings = Seq(
    organization := "com.malliina",
    version := "0.0.1",
    scalaVersion := "2.11.8",
    resolvers ++= Seq(
      Resolver.jcenterRepo,
      Resolver.bintrayRepo("malliina", "maven")
    ),
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk" % "1.11.75",
      PlayImport.cache,
      PlayImport.ws,
      utilPlayDep,
      utilPlayDep % Test classifier "tests"
    )
  )
}
