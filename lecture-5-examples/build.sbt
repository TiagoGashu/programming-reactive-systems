scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  "com.typesafe.akka"        %% "akka-actor-typed"       % "2.5.16",
  "com.typesafe.akka"        %% "akka-persistence-typed" % "2.5.16",
  "org.iq80.leveldb"          % "leveldb"                % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all"         % "1.8",
  "org.scalatest"            %% "scalatest"              % "3.0.5" % Test
)
