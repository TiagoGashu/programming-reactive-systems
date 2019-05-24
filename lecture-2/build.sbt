name := "lecture-2-the-actor-model"

version := "1.0"

scalaVersion := "2.12.6"

lazy val akkaVersion = "2.6.0-M1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)
