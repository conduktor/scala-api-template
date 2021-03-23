import BuildHelper._
import sbtbuildinfo.BuildInfoKey
import complete.DefaultParsers._

inThisBuild(
  List(
    organization := "io.conduktor",
    homepage := Some(url("https://www.conduktor.io/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check")
addCommandAlias("updates", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")



val upx = "UPX_COMPRESSION"

val zioVersion       = "1.0.5"
val zioConfigVersion = "1.0.1"
val tapirVersion     = "0.17.18"
val http4sVersion    = "0.21.20"
val circeVersion     = "0.13.0"

val effectDependencies = Seq(
  "dev.zio"              %% "zio"          % zioVersion,
  "dev.zio"              %% "zio-test"     % zioVersion % "test",
  "dev.zio"              %% "zio-test-sbt" % zioVersion % "test",
  "io.github.kitlangton" %% "zio-magic"    % "0.1.12"
)

val dbDependencies = Seq(
  "org.tpolecat" %% "skunk-core" % "0.0.24"
)

val httpDependencies = Seq(
  "org.http4s"                  %% "http4s-dsl"              % http4sVersion,
  "org.http4s"                  %% "http4s-blaze-server"     % http4sVersion,
  "org.http4s"                  %% "http4s-circe"            % http4sVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-zio"               % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-zio-http4s-server" % tapirVersion
)

val jwtDependencies = Seq(
  "com.github.jwt-scala" %% "jwt-circe" % "7.1.1",
  "com.auth0"             % "jwks-rsa"  % "0.6.1"
)

val jsonDependencies = Seq(
  "io.circe"                    %% "circe-core"       % circeVersion,
  "io.circe"                    %% "circe-generic"    % circeVersion,
  "io.circe"                    %% "circe-shapes"     % circeVersion,
  "io.circe"                    %% "circe-parser"     % circeVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion
)

val loggingDependencies = Seq(
  "dev.zio" %% "zio-logging-slf4j" % "0.5.8",
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

val embeddedPostgres = "com.opentable.components" % "otj-pg-embedded" % "0.13.3" % Test
val dbTestingStack   = Seq(embeddedPostgres)

val dependencies =
  effectDependencies ++ dbDependencies ++ httpDependencies ++ jsonDependencies ++ loggingDependencies ++ configDependencies ++ apiDocsDependencies ++ jwtDependencies

lazy val root = project
  .in(file("."))
  .enablePlugins(BuildInfoPlugin, sbtdocker.DockerPlugin, GraalVMNativeImagePlugin)
  .settings(
    skip in publish := true
  )
  .settings(stdSettings("api-template"))
  .settings(
    name := "api-template",
    buildInfoKeys := Seq[BuildInfoKey](organization, moduleName, name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "io.conduktor",
    buildInfoObject := "BuildInfo",
    libraryDependencies ++= dependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .settings(
//    graalVMNativeImageOptions ++= Seq(
//      "--allow-incomplete-classpath",
//      "--report-unsupported-elements-at-runtime",
//      "--initialize-at-build-time",
//      "--no-fallback",
//      "--enable-http",
//      "--enable-https",
//      // TODO below is not tested
//      "--enable-all-security-services",
//      "-H:+AddAllCharsets",
//      "--verbose",
//      //
//      "--no-server",
//      //
//      "--trace-object-instantiation=java.nio.DirectByteBuffer",
//      "--initialize-at-run-time=io.grpc.netty.shaded.io.netty.handler.ssl.JdkNpnApplicationProtocolNegotiator,io.grpc.netty.shaded.io.netty.handler.ssl.JettyNpnSslEngine"
//    ),

    docker / dockerfile := NativeDockerfile(file("Dockerfile")),
    docker / imageNames := Seq(ImageName(s"eu.gcr.io/conduktor/${name.value}:${dockerImageTag}")),
    docker / dockerBuildArguments := sys.env.get(upx).map(s => Map("upx_compression" -> s)).getOrElse(Map.empty),
    assembly / assemblyMergeStrategy := {
      case "META-INF/maven/org.webjars/swagger-ui/pom.properties" => MergeStrategy.singleOrError
      case x if x.endsWith("module-info.class")                   => MergeStrategy.discard
      case x                                                      =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

def dockerImageTag: String = {
  import sys.process._
  val regex       = """v\d+\.\d+\.\d+""".r.regex
  val versionTags = "git tag --points-at HEAD".!!.trim.split("\n").filter(_.matches(regex))
  val version     = versionTags.sorted(Ordering.String.reverse).headOption.map(_.replace("v", "")).getOrElse("latest")
  val upxSuffix   = sys.env.get(upx).map(s => s"-upx${s.replace("--", "-")}").getOrElse("")
  s"$version$upxSuffix"
}

// MIGRATION

val prisma = inputKey[Unit]("Database migration task.")
prisma := {
  // get the result of parsing
  val args: Seq[String] = spaceDelimited("<arg>").parsed

  val res = args match {
    case Seq("create") =>
      println("Creating migration SQL file...")
      MigrationCommands.createMigration
    case Seq("apply", "dev") =>
      println("Applying migrations to dev database...")
      MigrationCommands.applyMigrationDev
    case Seq("apply", "prod", "--force") =>
      println("Applying migrations to prod database...")
      MigrationCommands.applyProd_danger
    case Seq("status") =>
      println("Fetching migration status...")
      MigrationCommands.getMigrationStatus
    case Seq("validate") =>
      println("Validating schema...")
      MigrationCommands.validateSchema
    case _ => "Unknown command"
  }
  println(res)
}
addCommandAlias("migration", ";prisma")

