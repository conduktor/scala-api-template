import sbt.Keys._
import sbt._
import scalafix.sbt.ScalafixPlugin.autoImport.{scalafixSemanticdb, _}

object BuildHelper {
  val Scala213 = "2.13.5"

  val scalaReflectSettings = Seq(
    libraryDependencies ++= Seq("dev.zio" %% "izumi-reflect" % "1.0.0-M16")
  )

  def stdSettings(prjName: String) = Seq(
    name := s"$prjName",
    (ThisBuild / scalaVersion) := Scala213,
    libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.0" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    semanticdbEnabled := true,
    semanticdbOptions += "-P:semanticdb:synthetics:on",
    semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
    ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
    ThisBuild / scalafixDependencies ++= List(
      "com.github.liancheng" %% "organize-imports" % "0.5.0",
      "com.github.vovapolu"  %% "scaluzzi"         % "0.1.20"
    ),
    (Test / parallelExecution) := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true
  )

  implicit final class ModuleHelper(private val p: Project) extends AnyVal {
    def module: Project = p.in(file(p.id)).settings(stdSettings(p.id))
  }
}
