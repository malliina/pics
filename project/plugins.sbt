scalaVersion := "2.12.20"

val utilsVersion = "1.6.41"

Seq(
  "com.malliina" % "sbt-revolver-rollup" % utilsVersion,
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2",
  "org.scalameta" % "sbt-scalafmt" % "2.5.2",
  "com.eed3si9n" % "sbt-assembly" % "2.3.0",
  "com.github.sbt" % "sbt-native-packager" % "1.10.4"
) map addSbtPlugin
