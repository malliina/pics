scalaVersion := "2.12.10"

Seq(
  "com.malliina" % "play-live-reload" % "0.0.26",
  "com.malliina" % "sbt-nodejs" % "0.15.7",
  "com.malliina" % "sbt-packager" % "2.8.4",
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.10-0.6",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0",
  "org.scala-js" % "sbt-scalajs" % "0.6.32",
  "ch.epfl.scala" % "sbt-web-scalajs-bundler-sjs06" % "0.16.0",
  "com.eed3si9n" % "sbt-buildinfo" % "0.9.0",
  "ch.epfl.scala" % "sbt-bloop" % "1.4.0-RC1-190-ef7d8dba",
  "org.scalameta" % "sbt-scalafmt" % "2.3.0"
) map addSbtPlugin
