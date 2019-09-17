name := """play-scala-seed"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
val akkaVersion: String = sys.props.getOrElse("akka.version", "2.6.0-M7")

val akkaPersistenceJdbc = "3.5.2"

scalaVersion := "2.13.0"

libraryDependencies += guice
libraryDependencies ++= Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,

  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.github.dnvriend" %% "akka-persistence-jdbc" % akkaPersistenceJdbc,

  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,

 "org.postgresql" % "postgresql" % "42.2.5",

)

