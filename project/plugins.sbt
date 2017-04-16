scalaVersion := "2.10.6"

resolvers ++= Seq(
  Resolver.bintrayRepo("malliina", "maven"),
  Resolver.url("malliina bintray sbt", url("https://dl.bintray.com/malliina/sbt-plugins/"))(Resolver.ivyStylePatterns)
)

scalacOptions ++= Seq("-unchecked", "-deprecation")

addSbtPlugin("com.malliina" % "sbt-play" % "0.9.7")
