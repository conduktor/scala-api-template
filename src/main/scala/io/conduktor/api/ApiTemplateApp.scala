package io.conduktor.api

import io.conduktor.api.auth.JwtAuthService
import io.conduktor.api.config.AppConfig
import io.conduktor.api.config.HttpConfig
import io.conduktor.api.http.Server
import io.conduktor.api.repository.db.{DbPostRepository, DbSessionPool}
import io.conduktor.api.service.PostServiceLive
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.{App, ExitCode, ULayer, URIO, ZIO}
import zio.ZLayer
import io.conduktor.api.http.v1.PostRoutes
import zio.Has
import io.conduktor.api.config.DBConfig
import io.conduktor.api.config.Auth0Config

object ApiTemplateApp extends App {

  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "noop",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logLayerLive: ULayer[Logging]        =
    Slf4jLogger.make((context, message) => "[correlation-id = %s] %s".format(context(correlationId), message))

  import zio.magic._
  val apiRuntimelayers = ZLayer.fromSomeMagic[zio.ZEnv with Has[AppConfig], zio.ZEnv with PostRoutes.Env with Has[HttpConfig]](
    DbSessionPool.layer,
    DbPostRepository.layer,
    JwtAuthService.layer,
    PostServiceLive.layer,
    logLayerLive,
    ZLayer.fromService[AppConfig, Auth0Config](_.auth0),
    ZLayer.fromService[AppConfig, DBConfig](_.db),
    ZLayer.fromService[AppConfig, HttpConfig](_.http)
  )

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.serve.useForever
      .tapError(err => ZIO.effect(Option(err.getMessage).fold(err.printStackTrace())(println(_))))
      .provideSomeMagicLayer[zio.ZEnv with Has[AppConfig]](apiRuntimelayers)
      .provideSomeMagicLayer[zio.ZEnv](AppConfig.layer)
      .exitCode

}
