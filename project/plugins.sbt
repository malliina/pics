scalaVersion := "2.12.18"

val utilsVersion = "1.6.34"

Seq(
  "com.malliina" % "sbt-revolver-rollup" % utilsVersion,
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2",
  "org.scalameta" % "sbt-scalafmt" % "2.5.2",
  "com.eed3si9n" % "sbt-assembly" % "2.1.5"
) map addSbtPlugin
