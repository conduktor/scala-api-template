package io.conduktor.api

import io.conduktor.api.auth.{AuthService, JwtAuthService}
import io.conduktor.api.config.{AppConfig, Auth0Config, DBConfig, HttpConfig}
import io.conduktor.api.http.Server
import io.conduktor.api.http.v1.PostRoutes
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.repository.db.{DbPostRepository, DbSessionPool}
import io.conduktor.api.service.{PostService, PostServiceLive}
import zio.clock.Clock
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.{App, ExitCode, Has, ULayer, URIO, URLayer, ZIO, ZLayer, system}

object ApiTemplateApp extends App {

  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "noop",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logLayerLive: ULayer[Logging]        =
    Slf4jLogger.make((context, message) => "[correlation-id = %s] %s".format(context(correlationId), message))

  private val appConfig: ZLayer[system.System, Throwable, Has[AppConfig]] = AppConfig.layer

  private val authLayer: URLayer[Has[AppConfig] with Clock, Has[AuthService]] = (ZLayer.fromService[AppConfig, Auth0Config](_.auth0) ++ ZLayer.identity[Clock]) >>> JwtAuthService.layer

  val dbLayer: ZLayer[Has[AppConfig], Throwable, Has[PostRepository]] = (ZLayer.fromService[AppConfig, DBConfig](_.db) >>> DbSessionPool.layer) >>> DbPostRepository.layer

  val serviceLayer: URLayer[Has[PostRepository], Has[PostService]] = PostServiceLive.layer

  val apiRuntimelayers: ZLayer[Has[AppConfig] with Clock with Has[PostRepository], Throwable, Has[AuthService] with Has[PostService] with Logging] = authLayer ++ serviceLayer ++ logLayerLive

  val httpConfig = ZLayer.fromService[AppConfig, HttpConfig](_.http)

  private val requiringConfigs: ZLayer[Has[AppConfig] with Clock, Throwable, PostRoutes.Env] = (ZLayer.identity[Clock] ++ ZLayer.identity[Has[AppConfig]] ++ dbLayer) >>> apiRuntimelayers

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.serve.useForever
      .tapError(err => ZIO.effect(Option(err.getMessage).fold(err.printStackTrace())(println(_))))
      .provideLayer((ZLayer.identity[zio.ZEnv] ++ appConfig) >>> (ZLayer.identity[zio.ZEnv] ++ httpConfig ++ requiringConfigs))
      .exitCode

}
