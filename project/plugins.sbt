scalaVersion := "2.12.17"

val utilsVersion = "1.6.4"

Seq(
  "com.malliina" % "sbt-nodejs" % utilsVersion,
  "com.malliina" % "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.malliina" % "live-reload" % "0.5.0",
  "com.github.sbt" % "sbt-native-packager" % "1.9.11",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0",
  "org.scala-js" % "sbt-scalajs" % "1.12.0",
  "ch.epfl.scala" % "sbt-scalajs-bundler" % "0.21.1",
  "com.eed3si9n" % "sbt-buildinfo" % "0.11.0",
  "org.scalameta" % "sbt-scalafmt" % "2.5.0",
  "com.eed3si9n" % "sbt-assembly" % "1.2.0"
) map addSbtPlugin

libraryDependencies ++= Seq(
  "com.malliina" %% "primitives" % "3.4.0",
  "commons-codec" % "commons-codec" % "1.15"
)
