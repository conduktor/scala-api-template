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
        effect ++ db ++ http ++ json ++ logging ++ configurations ++ apiDocs ++ jwt ++ refined ++ Seq(newtype, flyway) ++ dbTestingStack
    )

// ### sbt tasks ###
val upx = "UPX_COMPRESSION"

val zioVersion       = "1.0.9"
val zioConfigVersion = "1.0.6"
val tapirVersion     = "0.17.19"
val http4sVersion    = "0.21.20"
val circeVersion     = "0.13.0"
val flywayVersion    = "7.10.0"

val effectDependencies = Seq(
  "dev.zio"              %% "zio"          % zioVersion,
  "dev.zio"              %% "zio-test"     % zioVersion % Test,
  "dev.zio"              %% "zio-test-sbt" % zioVersion % Test,
  "io.github.kitlangton" %% "zio-magic"    % "0.1.12"
)

val dbDependencies = Seq(
  "org.tpolecat" %% "skunk-core" % "0.0.24"
)

val httpDependencies = Seq(
  "org.http4s"                    %% "http4s-dsl"              % http4sVersion,
  "org.http4s"                    %% "http4s-blaze-server"     % http4sVersion,
  "org.http4s"                    %% "http4s-circe"            % http4sVersion,
  "com.softwaremill.sttp.tapir"   %% "tapir-zio"               % tapirVersion,
  "com.softwaremill.sttp.tapir"   %% "tapir-zio-http4s-server" % tapirVersion,
  "com.softwaremill.sttp.tapir"   %% "tapir-refined"           % tapirVersion,
  "com.softwaremill.sttp.shared"  %% "ws"                      % "1.2.5", //resolve a conflict between client3 and tapir
  "com.softwaremill.sttp.client3" %% "core"                    % "3.3.7" % Test,
  "com.softwaremill.sttp.client3" %% "httpclient-backend-zio"  % "3.3.7" % Test
)

val jwtDependencies = Seq(
  "com.github.jwt-scala" %% "jwt-circe" % "7.1.1",
  "com.auth0"             % "jwks-rsa"  % "0.6.1"
)

val jsonDependencies = Seq(
  "io.circe"                    %% "circe-core"       % circeVersion,
  "io.circe"                    %% "circe-generic"    % circeVersion,
  "io.circe"                    %% "circe-parser"     % circeVersion,
  "io.circe"                    %% "circe-refined"    % circeVersion,
  "io.circe"                    %% "circe-testing"    % circeVersion % Test,
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion
)

val loggingDependencies = Seq(
  "dev.zio"         %% "zio-logging-slf4j"            % "0.5.8",
  "com.google.cloud" % "google-cloud-logging-logback" % "0.120.2-alpha"
)

val configDependencies = Seq(
  "dev.zio" %% "zio-config"          % zioConfigVersion,
  "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion
)

val apiDocsDependencies = Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"       % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s"  % tapirVersion
)

val flywayDependencies = Seq(
  "org.postgresql" % "postgresql" % "42.2.22",
  "org.flywaydb" % "flyway-core" % flywayVersion
)

val embeddedPostgres = "com.opentable.components" % "otj-pg-embedded" % "0.13.3" % Test
val dbTestingStack   = Seq(embeddedPostgres)

val dependencies =
  effectDependencies ++
    dbDependencies ++
    httpDependencies ++
    jsonDependencies ++
    loggingDependencies ++
    configDependencies ++
    apiDocsDependencies ++
    jwtDependencies ++
    flywayDependencies ++
    Seq(
      "io.estatico" %% "newtype"      % "0.4.4",
      "eu.timepit"  %% "refined"      % "0.9.26",
      "eu.timepit"  %% "refined-cats" % "0.9.26"
    ) ++ dbTestingStack

lazy val dockerSettings = Seq(
  Docker / maintainer := "Conduktor LLC <support@conduktor.io>",
  Docker / daemonUser := "conduktor",
  Docker / dockerRepository := Some("eu.gcr.io"),
  Docker / packageName := sys.env.getOrElse("DOCKER_PACKAGE", ""),
  dockerUpdateLatest := true,
  dockerExposedPorts := Seq(8080),
  dockerBaseImage := "adoptopenjdk/openjdk11:alpine-jre",
  Docker / dockerCommands := dockerCommands.value.flatMap {
    case cmd @ Cmd("FROM", _) => List(cmd, Cmd("RUN", "apk update && apk add bash && apk add shadow"))
    case other                => List(other)
  }
)

lazy val root = project
  .in(file("."))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, DockerPlugin, AshScriptPlugin)
  .settings(
    (publish / skip) := true
  )
  .settings(stdSettings("api-template"))
  .settings(dockerSettings)
  .settings(
    name := "api-template",
    buildInfoKeys := Seq[BuildInfoKey](organization, moduleName, name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "io.conduktor",
    buildInfoObject := "BuildInfo",
    libraryDependencies ++= dependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    scalacOptions += "-Ymacro-annotations",
    scalacOptions -= "-Xfatal-warnings"
  )

def dockerImageTag: String = {
  import sys.process._
  val regex       = """v\d+\.\d+\.\d+""".r.regex
  val versionTags = "git tag --points-at HEAD".!!.trim.split("\n").filter(_.matches(regex))
  val version     = versionTags.sorted(Ordering.String.reverse).headOption.map(_.replace("v", "")).getOrElse("latest")
  val upxSuffix   = sys.env.get(upx).map(s => s"-upx${s.replace("--", "-")}").getOrElse("")
  s"$version$upxSuffix"
}

addCommandAlias("migrate-apply", "runMain io.conduktor.api.ApiMigrationApp")
