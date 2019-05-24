name := "lecture-2-the-actor-model"

version := "1.0"

scalaVersion := "2.12.6"

lazy val akkaVersion = "2.6.0-M1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.ning" %% "async-http-client" % "1.7.19",
  "org.jsoup" %% "jsoup" % "1.8.1",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)
