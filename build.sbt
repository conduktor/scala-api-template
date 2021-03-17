import BuildHelper._

inThisBuild(
  List(
    organization := "io.conduktor",
    homepage := Some(url("https://www.conduktor.io/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check")
addCommandAlias("up", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")

val zioVersion = "1.0.5"

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true
  )
  .settings(stdSettings("api-template"))
  .settings(buildInfoSettings("io.conduktor"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % zioVersion,
      "dev.zio" %% "zio-test"     % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
    )
  )
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
