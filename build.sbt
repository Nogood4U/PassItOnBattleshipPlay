name := "PassItOnBattleshipPlay"

version := "1.0"

lazy val `passitonbattleshipplay` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"
resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/"

scalaVersion := "2.12.2"
scalacOptions += "-Ypartial-unification"

libraryDependencies ++= Seq(ehcache, ws, specs2 % Test, guice)

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "6.1.0",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "6.1.0",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "6.1.0",
  "com.mohiva" %% "play-silhouette-persistence" % "6.1.0",
  "com.mohiva" %% "play-silhouette-testkit" % "6.1.0" % "test",
  "net.codingwell" %% "scala-guice" % "4.2.6",
  "com.iheart" %% "ficus" % "1.4.7",
  "com.typesafe.play" %% "play-slick" % "4.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "4.0.0",
  "mysql" % "mysql-connector-java" % "5.1.48",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.20.3-play27",
  "org.typelevel" %% "cats-core" % "2.0.0"
)
