import BuildHelper._
import Librairies._
import complete.DefaultParsers._
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoPlugin.autoImport.BuildInfoKey

ThisBuild / organization := "io.conduktor"
ThisBuild / homepage := Some(url("https://www.conduktor.io/"))
ThisBuild / licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / scalaVersion := "2.13.6"
ThisBuild / scalafmtCheck := true
ThisBuild / scalafmtSbtCheck := true
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbOptions += "-P:semanticdb:synthetics:on"
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision // use Scalafix compatible version
ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)
ThisBuild / scalafixDependencies ++= List(
  "com.github.liancheng" %% "organize-imports" % "0.5.0",
  "com.github.vovapolu"  %% "scaluzzi"         % "0.1.20"
)

// ### Aliases ###

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check")
addCommandAlias("updates", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")
addCommandAlias("migrate-apply", "runMain io.conduktor.api.ApiMigrationApp")

// ### Modules ###

/**
 * `root` is a "meta module".
 * It's the "main module" of this project but doesn't have a physical existence.
 * It represents the "current project" if you prefer, composed of modules.
 *
 * The `aggregate` setting will instruct sbt that when you're launching an sbt command, you want it applied to
 * all the aggregated modules
 */
lazy val root =
  Project(id = "scala-api-template", base = file("."))
    .disablePlugins(RevolverPlugin)
    .settings(noDoc: _*)
    .aggregate(api)

lazy val api =
  project
    .in(file("modules/api"))
    .enablePlugins(BuildInfoPlugin, JavaAppPackaging, DockerPlugin, AshScriptPlugin)
    .settings(noDoc: _*)
    .settings(commonSettings: _*)
    .settings(dockerSettings: _*)
    .settings(
      Compile / mainClass := Some("io.conduktor.api.ApiTemplateApp"),
      buildInfoKeys := Seq[BuildInfoKey](organization, moduleName, name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := "io.conduktor",
      buildInfoObject := "BuildInfo",
      Revolver.enableDebugging(),
      libraryDependencies ++=
        effect ++ db ++ http ++ json ++ logging ++ configurations ++ apiDocs ++ jwt ++ refined ++ Seq(newtype) ++ flyway ++ dbTestingStack
    )
