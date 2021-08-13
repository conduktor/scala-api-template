package io.conduktor.api

import io.conduktor.api.auth.{AuthService, JwtAuthService}
import io.conduktor.api.config.{AppConfig, Auth0Config, DBConfig}
import io.conduktor.api.db.{DatabaseMigrationService, DbSessionPool, FlywayDatabaseMigrationService}
import io.conduktor.api.http.Server
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.service.{PostService, PostServiceLive}

import zio.clock.Clock
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.random.Random
import zio.{App, ExitCode, Has, RIO, RLayer, ULayer, URIO, ZIO, ZLayer}

object ApiTemplateApp extends App {

  type AppEnv = zio.ZEnv with Server.ServerEnv with Has[DBConfig] with Has[DatabaseMigrationService]

  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "noop",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logLayerLive: ULayer[Logging]        =
    Slf4jLogger.make((context, message) => "[correlation-id = %s] %s".format(context(correlationId), message))

  val authLayer: RLayer[Has[Auth0Config] with Clock with Logging, Has[AuthService]] = JwtAuthService.layer

  val dbLayer: RLayer[Has[DBConfig], Has[PostRepository.Pool]] = DbSessionPool.layer >>> PostRepository.Pool.live

  val serviceLayer: RLayer[Has[PostRepository.Pool] with Random with Logging, Has[PostService]] = PostServiceLive.layer

  import zio.magic._
  private val env: RLayer[zio.ZEnv, AppEnv] =
    ZLayer.fromSomeMagic[zio.ZEnv, AppEnv](
      AppConfig.allLayers,
      dbLayer,
      authLayer,
      serviceLayer,
      logLayerLive,
      FlywayDatabaseMigrationService.layer
    )

  val migrateDatabase: RIO[Has[DBConfig] with Has[DatabaseMigrationService], Unit] = for {
    conf    <- ZIO.service[DBConfig]
    service <- ZIO.service[DatabaseMigrationService]
    _       <- service.migrate().when(conf.migrate)
  } yield ()

  val program: RIO[AppEnv, ExitCode] = for {
    _        <- migrateDatabase
                  // injecting separately, as repository layer verify the db schema on init
                  .injectCustom(AppConfig.dbOnlyLayer, FlywayDatabaseMigrationService.layer)
                  .orDie
    exitCode <- Server.serve.useForever
                  .tapError(err => ZIO.effect(Option(err.getMessage).fold(err.printStackTrace())(println(_))))
                  .exitCode
  } yield exitCode

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = program
    .provideCustomLayer(env)
    .orDie

}
