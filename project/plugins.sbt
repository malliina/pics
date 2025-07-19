scalaVersion := "2.12.20"

Seq(
  "com.malliina" % "sbt-revolver-rollup" % "1.6.55",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2",
  "org.scalameta" % "sbt-scalafmt" % "2.5.5",
  "com.eed3si9n" % "sbt-assembly" % "2.3.1",
  "com.github.sbt" % "sbt-native-packager" % "1.11.1"
) map addSbtPlugin
