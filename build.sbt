lazy val jna = "net.java.dev.jna" % "jna" % "4.5.0"
lazy val jnaPlatform = "net.java.dev.jna" % "jna-platform" % "4.5.0"
lazy val junitInterface = "com.novocode" % "junit-interface" % "0.11"

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      organization := "org.scala-sbt.ipcsocket"
    ),
    name := "ipcsocket",
    libraryDependencies ++= Seq(jna, jnaPlatform, junitInterface % Test),
    crossPaths := false,
    autoScalaLibrary := false,
  )
