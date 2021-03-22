import sbt.Keys._
import sbt._
import scalafix.sbt.ScalafixPlugin.autoImport.{ scalafixSemanticdb, _ }

object BuildHelper {
  val Scala213 = "2.13.5"

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  ) ++ {
    if (sys.env.contains("CI")) {
      Seq("-Xfatal-warnings") // to enable Scalafix
    } else {
      Nil
    }
  }

  private val std2xOptions = Seq(
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-missing-interpolator,-type-parameter-shadow,-byname-implicit",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )

  private def optimizerOptions(optimize: Boolean) =
    if (optimize)
      Seq(
        "-opt:l:inline"
      )
    else Nil

  val scalaReflectSettings = Seq(
    libraryDependencies ++= Seq("dev.zio" %% "izumi-reflect" % "1.0.0-M16")
  )

  def extraOptions(optimize: Boolean): Seq[String] =
    Seq(
      "-Ywarn-unused:params,-implicits"
    ) ++ std2xOptions ++ optimizerOptions(optimize)

  def stdSettings(prjName: String) = Seq(
    name := s"$prjName",
    scalaVersion in ThisBuild := Scala213,
    scalacOptions := stdOptions ++ extraOptions(optimize = !isSnapshot.value),
    libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full),
    semanticdbEnabled := true,
    semanticdbOptions += "-P:semanticdb:synthetics:on",
    semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
    ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
    ThisBuild / scalafixDependencies ++= List(
      "com.github.liancheng" %% "organize-imports" % "0.4.4",
      "com.github.vovapolu"  %% "scaluzzi"         % "0.1.16"
    ),
    parallelExecution in Test := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true
  )

  implicit class ModuleHelper(p: Project) {
    def module: Project = p.in(file(p.id)).settings(stdSettings(p.id))
  }
}
