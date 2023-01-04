import java.nio.file.{ Files, Path, Paths }
import java.io.InputStream
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ArrayBuffer
import scala.util.Try
import scala.sys.process._

val jnaVersion = "5.12.0"
val jna = "net.java.dev.jna" % "jna" % jnaVersion

val jnaPlatform = "net.java.dev.jna" % "jna-platform" % jnaVersion
val junitInterface = "com.novocode" % "junit-interface" % "0.11"
val nativePlatform = settingKey[String]("The target platform")
val nativeArch = settingKey[String]("The target architecture")
val nativeArtifact = settingKey[Path]("The target artifact location")
val nativeBuild = taskKey[Path]("Build the native artifact")
val nativeCompiler = settingKey[String]("The compiler for native compilation")
val nativeCompileOptions = settingKey[Seq[String]]("The native compilation options")
val nativeIncludes = settingKey[Seq[String]]("The native include paths")
val buildDarwin = taskKey[Path]("Build fat binary for x86_64 and arm64 on mac os")
val buildDarwinX86_64 = taskKey[Path]("Build mac native library for x86_64")
val buildDarwinArm64 = taskKey[Path]("Build mac native library for arm64")
val buildLinuxX86_64 = taskKey[Path]("Build Linux native library for x86_64")
val buildLinuxAarch64 = taskKey[Path]("Build Linux native library for Aarch64")
val buildWin32X86_64 = taskKey[Path]("Build windows native library for x86_64")

val isMac = scala.util.Properties.isMac
val isWin = scala.util.Properties.isWin
val libShortName = "sbtipcsocket"
val platforms = Map(
  "win32" -> s"$libShortName.dll",
  "darwin" -> s"lib$libShortName.dylib",
  "linux" -> s"lib$libShortName.so"
)

buildDarwin := {
  if (!(buildDarwin / skip).value) {
    val fatBinary =
      (Compile / resourceDirectory).value.toPath / "darwin" / "x86_64" / platforms("darwin")
    val x86 = buildDarwinX86_64.value.toString
    val arm = buildDarwinArm64.value.toString
    val logger = streams.value.log
    scala.util.Try(eval(Seq("lipo", "-create", "-o", s"$fatBinary", x86, arm), logger))
    fatBinary
  } else (Compile / resourceDirectory).value.toPath / "darwin" / "x86_64" / platforms("darwin")
}

inThisBuild(
  List(
    organization := "org.scala-sbt.ipcsocket",
    organizationName := "sbt",
    organizationHomepage := Some(url("http://scala-sbt.org/")),
    homepage := scmInfo.value map (_.browseUrl),
    scmInfo := Some(
      ScmInfo(url("https://github.com/sbt/ipcsocket"), "git@github.com:sbt/ipcsocket.git")
    ),
    Compile / javacOptions ++= Seq("-h", (baseDirectory.value / "jni").toString),
    Compile / doc / javacOptions := Nil,
    developers := List(
      Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", url("https://github.com/eed3si9n"))
    ),
    isSnapshot := (isSnapshot or version(_ endsWith "-SNAPSHOT")).value,
    description := "IPC: Unix Domain Socket and Windows Named Pipes for Java",
    licenses := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    nativeArch := "x86_64",
    nativeCompiler := "gcc",
    nativeCompileOptions := "-shared" :: "-O2" :: "-Wall" :: "-Wextra" :: Nil,
    nativePlatform := (System.getProperty("os.name").head.toLower match {
      case 'm' => "darwin"
      case 'w' => "win32"
      case 'l' => "linux"
      case _   => "unknown"
    }),
    nativeIncludes := {
      val home =
        javaHome.value.getOrElse(throw new IllegalStateException("No java home defined"))
      s"-I${home / "include"}" :: s"-I${home / "include" / nativePlatform.value}" :: Nil
    },
  )
)
name := "ipcsocket"
libraryDependencies ++= Seq(jna, jnaPlatform, junitInterface % Test)
crossPaths := false
autoScalaLibrary := false
nativeLibrarySettings("darwinX86_64")
nativeLibrarySettings("darwinArm64")
nativeLibrarySettings("linuxX86_64")
nativeLibrarySettings("linuxAarch64")
nativeLibrarySettings("win32X86_64")
if (!isWin) (buildWin32X86_64 / nativeCompiler := "x86_64-w64-mingw32-gcc") :: Nil else Nil
buildLinuxAarch64 / nativeCompiler := "aarch64-linux-gnu-gcc"
buildWin32X86_64 / skip := {
  val s = streams.value
  Try(s"which ${(buildWin32X86_64 / nativeCompiler).value}".!!).fold(_ => {
    s.log.warn(
      s"skipping buildWin32X86_64 because ${(buildWin32X86_64 / nativeCompiler).value} was not found"
    )
    true
  }, _.isEmpty)
}
Test / fork := true
clangfmt / fileInputs += baseDirectory.value.toGlob / "jni" / "*.c"
commands += Command.command("buildNativeArtifacts") { state =>
  "buildLinuxX86_64" :: "buildLinuxAarch64" :: "buildDarwin" :: "buildWin32X86_64" :: state
}

Global / javaHome := {
  System.getProperty("java.home") match {
    case null                   => None
    case h if h.endsWith("jre") => Some(Paths.get(h).getParent.toFile)
    case h                      => Some(file(h))
  }
}

def nativeLibrarySettings(platform: String): Seq[Setting[_]] = {
  val key = TaskKey[Path](s"build${platform.head.toUpper}${platform.tail}")
  val shortPlatform =
    platform match {
      case p if p.startsWith("darwin") => "darwin"
      case p if p.startsWith("linux")  => "linux"
      case p if p.startsWith("win32")  => "win32"
    }
  Def.settings(
    key / nativeArch := {
      val orig = (key / nativeArch).value
      if (platform.contains("Arm64")) "arm64"
      else if (platform.contains("Aarch64")) "aarch64"
      else orig
    },
    key / nativeCompileOptions ++= (shortPlatform match {
      case "win32" =>
        Seq("-D__WIN__", "-lkernel32", "-ladvapi32", "-ffreestanding", "-fdiagnostics-color=always")
      case "darwin" => Seq("-arch", (key / nativeArch).value)
      case _        => Nil
    }),
    key / nativeArtifact := {
      val name = platforms.get(shortPlatform).getOrElse(s"lib$libShortName.so")
      val resourceDir = (Compile / resourceDirectory).value.toPath
      val targetDir = (Compile / target).value.toPath
      val arch = (key / nativeArch).value
      if (shortPlatform == "darwin") targetDir / platform / arch / name
      else resourceDir / shortPlatform / arch / name
    },
    key / fileInputs += {
      val glob = if (shortPlatform == "win32") "*Win*.{c,h}" else "*Unix*.{c,h}"
      baseDirectory.value.toGlob / "jni" / glob,
    },
    key / skip := ((ThisBuild / nativePlatform).value match {
      case `platform`                    => false
      case p if shortPlatform == "win32" => false
      case p                             => !platform.startsWith(p)
    }),
    key / nativeBuild := {
      val artifact = (key / nativeArtifact).value
      val inputs = key.inputFiles.collect {
        case i if i.getFileName.toString.endsWith(".c") => i.toString
      }
      val options = (key / nativeCompileOptions).value
      val compiler = (key / nativeCompiler).value
      val logger = streams.value.log
      val includes = (key / nativeIncludes).value
      val s = streams.value
      s.log.info(s"""compiling ${inputs.mkString(", ")}""")
      if (key.inputFileChanges.hasChanges || !artifact.toFile.exists) {
        Files.createDirectories(artifact.getParent)
        eval(Seq(compiler, "-o", artifact.toString) ++ includes ++ options ++ inputs, logger)
      }
      s.log.info(s"""done compiling $artifact""")
      artifact
    },
    key := {
      if ((key / skip).value) (key / nativeArtifact).value
      else (key / nativeBuild).value
    },
    key := key.dependsOn(Compile / compile).value,
  )
}

def eval(cmd: Seq[String], logger: Logger): Unit = {
  logger.debug(s"Running compilation: ${cmd mkString " "}")
  val proc = new java.lang.ProcessBuilder(cmd: _*).start()
  val thread = new Thread() {
    setDaemon(true)
    start()
    val is = proc.getInputStream
    val es = proc.getErrorStream
    val isOutput = new ArrayBuffer[Int]
    val esOutput = new ArrayBuffer[Int]
    def drain(stream: InputStream, buffer: ArrayBuffer[Int], isError: Boolean): Unit = {
      while (stream.available > 0) {
        stream.read match {
          case 10 =>
            val msg = new String(buffer.map(_.toByte).toArray)
            buffer.clear()
            if (isError) logger.error(msg) else logger.info(msg)
          case c => buffer += c
        }
      }
    }
    def drain(): Unit = {
      drain(is, isOutput, false)
      drain(es, esOutput, true)
    }
    override def run(): Unit = {
      while (proc.isAlive) {
        drain()
        Thread.sleep(10)
      }
      drain()
    }
  }
  proc.waitFor(1, TimeUnit.MINUTES)
  thread.join()
  if (proc.exitValue != 0)
    throw new IllegalStateException(
      s"'${cmd mkString " "}' exited with ${proc.exitValue}"
    )
}
