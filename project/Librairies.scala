import sbt._

object Librairies {

  val zioVersion       = "1.0.9"
  val zioConfigVersion = "1.0.6"
  val tapirVersion     = "0.17.19"
  val http4sVersion    = "0.21.20"
  val circeVersion     = "0.13.0"
  val refinedVersion   = "0.9.26"

  val newtype           = "io.estatico" %% "newtype"            % "0.4.4"
  val refinedScalacheck = "eu.timepit"  %% "refined-scalacheck" % refinedVersion
  val flyway            = Seq(
    "org.flywaydb"   % "flyway-core" % "7.10.0",
    "org.postgresql" % "postgresql"  % "42.2.22"
  )

  val refined: Seq[ModuleID] = Seq(
    "eu.timepit" %% "refined"      % refinedVersion,
    "eu.timepit" %% "refined-cats" % refinedVersion
  )

  val effect = Seq(
    "dev.zio"              %% "zio"          % zioVersion,
    "dev.zio"              %% "zio-test"     % zioVersion % Test,
    "dev.zio"              %% "zio-test-sbt" % zioVersion % Test,
    "io.github.kitlangton" %% "zio-magic"    % "0.1.12"
  )

  val db = Seq(
    "org.tpolecat" %% "skunk-core" % "0.0.24"
  )

  val http = Seq(
    "org.http4s"                    %% "http4s-dsl"              % http4sVersion,
    "org.http4s"                    %% "http4s-blaze-server"     % http4sVersion,
    "org.http4s"                    %% "http4s-circe"            % http4sVersion,
    "com.softwaremill.sttp.tapir"   %% "tapir-zio"               % tapirVersion,
    "com.softwaremill.sttp.tapir"   %% "tapir-zio-http4s-server" % tapirVersion,
    "com.softwaremill.sttp.tapir"   %% "tapir-refined"           % tapirVersion,
    "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"        % tapirVersion,
    "com.softwaremill.sttp.shared"  %% "ws"                      % "1.2.5", //resolve a conflict between client3 and tapir
    "com.softwaremill.sttp.client3" %% "core"                    % "3.3.7" % Test,
    "com.softwaremill.sttp.client3" %% "httpclient-backend-zio"  % "3.3.7" % Test
  )

  val jwt = Seq(
    "com.github.jwt-scala" %% "jwt-circe" % "7.1.1",
    "com.auth0"             % "jwks-rsa"  % "0.6.1"
  )

  val json = Seq(
    "io.circe" %% "circe-core"    % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser"  % circeVersion,
    "io.circe" %% "circe-refined" % circeVersion,
    "io.circe" %% "circe-testing" % circeVersion % Test
  )

  val logging = Seq(
    "dev.zio"         %% "zio-logging-slf4j"            % "0.5.8",
    "com.google.cloud" % "google-cloud-logging-logback" % "0.120.2-alpha"
  )

  val configurations = Seq(
    "dev.zio" %% "zio-config"          % zioConfigVersion,
    "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
    "dev.zio" %% "zio-config-typesafe" % zioConfigVersion
  )

  val apiDocs = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"       % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s"  % tapirVersion
  )

  val embeddedPostgres = "com.opentable.components" % "otj-pg-embedded" % "0.13.3" % Test
  val dbTestingStack   = Seq(embeddedPostgres)

}
