import sbt._

object Librairies {

  val zioVersion       = "1.0.15"
  val zioConfigVersion = "2.0.4"
  val tapirVersion     = "1.0.1"
  val http4sVersion    = "0.23.12"
  val circeVersion     = "0.14.2"
  val refinedVersion   = "0.9.29"
  val sttpVersion      = "3.6.2"
  val slf4jVersion     = "1.7.36"

  val newtype           = "io.estatico" %% "newtype"            % "0.4.4"
  val refinedScalacheck = "eu.timepit"  %% "refined-scalacheck" % refinedVersion
  val flyway            = Seq(
    "org.flywaydb"   % "flyway-core" % "8.5.13",
    "org.postgresql" % "postgresql"  % "42.4.0"
  )

  val refined: Seq[ModuleID] = Seq(
    "eu.timepit" %% "refined"      % refinedVersion,
    "eu.timepit" %% "refined-cats" % refinedVersion
  )

  val effect = Seq(
    "dev.zio"              %% "zio"          % zioVersion,
    "dev.zio"              %% "zio-test"     % zioVersion % Test,
    "dev.zio"              %% "zio-test-sbt" % zioVersion % Test,
    "io.github.kitlangton" %% "zio-magic"    % "0.3.12"
  )

  val db = Seq(
    "org.tpolecat" %% "skunk-core"       % "0.3.1",
    "dev.zio"      %% "zio-interop-cats" % "3.3.0"
  )

  val http = Seq(
    "org.http4s"                  %% "http4s-dsl"               % http4sVersion,
    "org.http4s"                  %% "http4s-blaze-server"      % http4sVersion,
    "org.http4s"                  %% "http4s-circe"             % http4sVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-zio1"               % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server-zio1" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-refined"            % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-newtype"            % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe"         % tapirVersion
  )

  val client = Seq(
    "com.softwaremill.sttp.client3" %% "core" % sttpVersion % Test,
    "com.softwaremill.sttp.client3" %% "zio1" % sttpVersion % Test
  )

  val jwt = Seq(
    "com.github.jwt-scala" %% "jwt-circe" % "9.0.5",
    "com.auth0"             % "jwks-rsa"  % "0.21.1"
  )

  val json = Seq(
    "io.circe" %% "circe-core"    % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser"  % circeVersion,
    "io.circe" %% "circe-refined" % circeVersion,
    "io.circe" %% "circe-testing" % circeVersion % Test
  )

  val logging = Seq(
    "dev.zio"       %% "zio-logging-slf4j" % "0.5.14",
    "ch.qos.logback" % "logback-classic"   % "1.2.11",
    "org.slf4j"      % "jul-to-slf4j"      % slf4jVersion,
    "org.slf4j"      % "log4j-over-slf4j"  % slf4jVersion,
    "org.slf4j"      % "jcl-over-slf4j"    % slf4jVersion,
  )

  val configurations = Seq(
    "dev.zio" %% "zio-config"          % zioConfigVersion,
    "dev.zio" %% "zio-config-refined"  % zioConfigVersion,
    "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
    "dev.zio" %% "zio-config-typesafe" % zioConfigVersion
  )

  val apiDocs = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion
  )

  val embeddedPostgres = "com.opentable.components" % "otj-pg-embedded" % "1.0.1" % Test
  val dbTestingStack   = Seq(embeddedPostgres)

}
