scalaVersion := "2.12.11"

Seq(
  "com.malliina" % "play-live-reload" % "0.0.27",
  "com.malliina" % "sbt-nodejs" % "0.16.1",
  "com.malliina" % "sbt-packager" % "2.8.4",
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.10-0.6",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0",
  "org.scala-js" % "sbt-scalajs" % "0.6.33",
  "ch.epfl.scala" % "sbt-web-scalajs-bundler-sjs06" % "0.17.0",
  "com.eed3si9n" % "sbt-buildinfo" % "0.9.0",
  "ch.epfl.scala" % "sbt-bloop" % "1.4.1",
  "org.scalameta" % "sbt-scalafmt" % "2.4.0"
) map addSbtPlugin
