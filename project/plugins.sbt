scalaVersion := "2.12.18"

val utilsVersion = "1.6.19"

Seq(
  "com.malliina" % "sbt-nodejs" % utilsVersion,
  "com.malliina" % "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.malliina" % "live-reload" % "0.5.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.1",
  "org.scala-js" % "sbt-scalajs" % "1.13.2",
  "com.eed3si9n" % "sbt-buildinfo" % "0.11.0",
  "org.scalameta" % "sbt-scalafmt" % "2.5.2",
  "com.eed3si9n" % "sbt-assembly" % "2.1.1"
) map addSbtPlugin

libraryDependencies ++= Seq(
  "com.malliina" %% "primitives" % "3.4.5",
  "commons-codec" % "commons-codec" % "1.15"
)
