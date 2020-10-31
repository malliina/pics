scalaVersion := "2.12.12"

Seq(
//  "com.malliina" % "play-live-reload" % "0.1.0",
  "com.malliina" % "sbt-nodejs" % "1.0.0",
  "com.malliina" % "sbt-packager" % "2.9.0",
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.typesafe.sbt" % "sbt-native-packager" % "1.7.6",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
//  "com.vmunier" % "sbt-web-scalajs" % "1.0.11",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0",
  "org.scala-js" % "sbt-scalajs" % "1.2.0",
  "ch.epfl.scala" % "sbt-scalajs-bundler" % "0.18.0",
  "com.eed3si9n" % "sbt-buildinfo" % "0.10.0",
  "ch.epfl.scala" % "sbt-bloop" % "1.4.4",
  "org.scalameta" % "sbt-scalafmt" % "2.4.0",
  "io.spray" % "sbt-revolver" % "0.9.1"
) map addSbtPlugin
