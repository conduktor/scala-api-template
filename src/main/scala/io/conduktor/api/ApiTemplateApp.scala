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

object ApiTemplateApp extends App {

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
  private val env: RLayer[zio.ZEnv, Server.Env] =
    ZLayer.fromSomeMagic[zio.ZEnv, Server.Env](AppConfig.layer, dbLayer, authLayer, serviceLayer, logLayerLive)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.serve.useForever
      .tapError(err => ZIO.effect(Option(err.getMessage).fold(err.printStackTrace())(println(_))))
      .provideSomeLayer(env)
      .exitCode

}
