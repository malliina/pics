scalaVersion := "2.12.10"

resolvers ++= Seq(
  ivyRepo("bintray-sbt-plugin-releases", "https://dl.bintray.com/content/sbt/sbt-plugin-releases"),
  ivyRepo("malliina bintray sbt", "https://dl.bintray.com/malliina/sbt-plugins/"),
  Resolver.bintrayRepo("malliina", "maven")
)
scalacOptions ++= Seq("-unchecked", "-deprecation", "-language:implicitConversions")
classpathTypes += "maven-plugin"

Seq(
  "com.typesafe.play" % "sbt-plugin" % "2.8.0",
  "com.malliina" % "sbt-utils-maven" % "0.15.1",
  "com.malliina" % "sbt-nodejs" % "0.15.1",
  "com.malliina" %% "sbt-packager" % "2.8.2",
  "com.malliina" % "sbt-filetree" % "0.4.0",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "com.typesafe.sbt" % "sbt-less" % "1.1.2",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.6",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0",
  "org.scala-js" % "sbt-scalajs" % "0.6.31",
  "ch.epfl.scala" % "sbt-web-scalajs-bundler" % "0.14.0",
  "com.eed3si9n" % "sbt-buildinfo" % "0.9.0",
  "ch.epfl.scala" % "sbt-bloop" % "1.3.4",
  "org.scalameta" % "sbt-scalafmt" % "2.3.0"
) map addSbtPlugin

def ivyRepo(name: String, urlString: String) =
  Resolver.url(name, url(urlString))(Resolver.ivyStylePatterns)
