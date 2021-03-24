package io.conduktor.api.app

import io.conduktor.api.auth.UserAuthenticationLayer.AuthService
import io.conduktor.api.config.AppConfig
import io.conduktor.api.db.repository.PostRepository
import io.conduktor.api.db.{DbSession, DbSessionPool}
import io.conduktor.api.server.Server
import zio.logging._
import zio.magic.ZioProvideMagicOps
import zio.{App, ExitCode, ULayer, URIO, ZIO}
import zio.logging.slf4j.Slf4jLogger

object ApiTemplateApp extends App {

  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "noop",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logLayerLive: ULayer[Logging] =
    Slf4jLogger.make((context, message) => "[correlation-id = %s] %s".format(context(correlationId), message))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program
      .provideCustomMagicLayer(
        DbSessionPool.live,
        DbSession.live,
        PostRepository.live,
        AuthService.live,
        AppConfig.layer.project(_.db),
        AppConfig.layer.project(_.auth0),
        logLayerLive
      ).tapError(
      err => ZIO.effect(Option(err.getMessage).fold(err.printStackTrace())(println(_)))
     )
      .exitCode

  private val program = for {
    _ <- Server.serve
  } yield ()
}
