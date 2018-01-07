lazy val jna = "net.java.dev.jna" % "jna" % "4.5.0"
lazy val jnaPlatform = "net.java.dev.jna" % "jna-platform" % "4.5.0"

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      organization := "org.scala-sbt.ipcsocket"
    ),
    name := "ipcsocket",
    libraryDependencies ++= Seq(jna, jnaPlatform),
    crossPaths := false,
    autoScalaLibrary := false,
  )
