import com.typesafe.sbt.packager.Keys.{daemonUser, maintainer, packageName}
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import sbt._

object BuildHelper {

  val commonSettings = Seq(
    libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    javacOptions ++= Seq("-source", "17", "-target", "17"),
    scalacOptions ++= Seq("-Ymacro-annotations", "-Xsource:3", "-target:17"),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    (Test / parallelExecution) := true,
    (Test / fork)              := true
  ) ++ noDoc

  lazy val dockerSettings = Seq(
    Docker / maintainer       := "Conduktor Inc <support@conduktor.io>",
    Docker / daemonUser       := "conduktor",
    Docker / dockerRepository := Some("eu.gcr.io"),
    Docker / packageName      := sys.env.getOrElse("DOCKER_PACKAGE", ""),
    dockerUpdateLatest        := true,
    dockerExposedPorts        := Seq(8080),
    dockerBaseImage           := "adoptopenjdk/openjdk17:alpine-jre"
  ) ++ sys.env.get("RELEASE_TAG").map(v => Seq(Docker / version := v)).getOrElse(Seq.empty)

  lazy val noDoc = Seq(
    (Compile / doc / sources)                := Seq.empty,
    (Compile / packageDoc / publishArtifact) := false
  )

  /**
   * Copied from Cats
   */
  lazy val noPublishSettings = Seq(
    publish         := {},
    publishLocal    := {},
    publishM2       := {},
    publishArtifact := false
  )
}
