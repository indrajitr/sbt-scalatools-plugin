sbtPlugin := true

organization := "org.scala-tools.sbt"

name := "sbt-scalatools-plugin"

version := "0.0.1-SNAPSHOT"

licenses += ("Apache License, Version 2.0", url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

scalacOptions := Seq("-deprecation")

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Compile, packageSrc) := false

publishTo <<= (version) { version =>
  val snapshot = "Nexus Repository for Snapshots" at "http://nexus.scala-tools.org/content/repositories/snapshots/"
  val release  = "Nexus Repository for Releases"  at "http://nexus.scala-tools.org/content/repositories/releases/"
  if (version endsWith "-SNAPSHOT") Some(snapshot) else Some(release)
}
