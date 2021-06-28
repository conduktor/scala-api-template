package io.conduktor.api

import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.{App, ExitCode, ULayer, URIO, ZIO}
import io.conduktor.api.config.DBConfig
import io.conduktor.api.migration.{DatabaseMigrationService, FlywayDatabaseMigrationService}

object ApiMigrationApp extends App {

  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "noop",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logLayerLive: ULayer[Logging]        =
    Slf4jLogger.make((context, message) => "[correlation-id = %s] %s".format(context(correlationId), message))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    DatabaseMigrationService
      .migrate()
      .tapError(err => ZIO.effect(Option(err.getMessage).fold(err.printStackTrace())(println(_))))
      .provideLayer(FlywayDatabaseMigrationService.layer)
      .provideLayer(DBConfig.layer ++ logLayerLive)
      .exitCode
}
