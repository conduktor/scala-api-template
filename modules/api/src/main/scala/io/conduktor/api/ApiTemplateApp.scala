package io.conduktor.api

import io.conduktor.api.auth.{AuthService, JwtAuthService}
import io.conduktor.api.config.{AppConfig, Auth0Config, DBConfig}
import io.conduktor.api.http.Server
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.repository.db.{DbPostRepository, DbSessionPool}
import io.conduktor.api.service.{PostService, PostServiceLive}
import zio.clock.Clock
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.{App, ExitCode, Has, RLayer, ULayer, URIO, ZIO, ZLayer}
import io.conduktor.api.migration.DatabaseMigrationService
import io.conduktor.api.migration.FlywayDatabaseMigrationService
import zio.RIO

object ApiTemplateApp extends App {

  type AppEnv = zio.ZEnv with Server.Env with Has[DBConfig] with Has[DatabaseMigrationService]

  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "noop",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logLayerLive: ULayer[Logging]        =
    Slf4jLogger.make((context, message) => "[correlation-id = %s] %s".format(context(correlationId), message))

  val authLayer: RLayer[Has[Auth0Config] with Clock, Has[AuthService]] = JwtAuthService.layer

  val dbLayer: RLayer[Has[DBConfig], Has[PostRepository]] = DbSessionPool.layer >>> DbPostRepository.layer

  val serviceLayer: RLayer[Has[PostRepository], Has[PostService]] = PostServiceLive.layer

  import zio.magic._
  private val env: RLayer[zio.ZEnv, AppEnv] =
    ZLayer.fromSomeMagic[zio.ZEnv, AppEnv](
      AppConfig.layer,
      dbLayer,
      authLayer,
      serviceLayer,
      logLayerLive,
      FlywayDatabaseMigrationService.layer
    )

  val migrateDatabase: RIO[Has[DBConfig] with Has[DatabaseMigrationService], Unit] = for {
    conf    <- ZIO.service[DBConfig]
    service <- ZIO.service[DatabaseMigrationService]
    _       <- if (conf.dbMigration) service.migrate() else ZIO.unit
  } yield ()

  val program: URIO[AppEnv, ExitCode] = for {
    _        <- migrateDatabase.orDie
    exitCode <- Server.serve.useForever
                  .tapError(err => ZIO.effect(Option(err.getMessage).fold(err.printStackTrace())(println(_))))
                  .exitCode
  } yield exitCode

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program.provideSomeLayer(env.orDie)

}
