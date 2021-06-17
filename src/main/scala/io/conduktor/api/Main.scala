package io.conduktor.api

import io.conduktor.api.auth.JwtAuthService
import io.conduktor.api.config.AppConfig
import io.conduktor.api.http.Server
import io.conduktor.api.repository.db.{DbPostRepository, DbSessionPool}
import io.conduktor.api.service.PostServiceLive
import zio.clock.Clock
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.magic.ZioProvideMagicOps
import zio.{App, ExitCode, Has, ULayer, URIO, ZIO, ZLayer}

import java.time.{DateTimeException, Instant, OffsetDateTime, ZoneId}

object Main extends App {

  val javaClock: ZLayer[Clock, DateTimeException, Has[java.time.Clock]] = {
    def clockFromOffset(now: OffsetDateTime): java.time.Clock =
      new java.time.Clock {
        override def getZone: ZoneId                         = now.getOffset
        override def withZone(zone: ZoneId): java.time.Clock = clockFromOffset(now.atZoneSameInstant(zone).toOffsetDateTime)
        override def instant(): Instant                      = now.toInstant
      }

    zio.clock.currentDateTime.map(clockFromOffset).toLayer
  }

  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "noop",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logLayerLive: ULayer[Logging]        =
    Slf4jLogger.make((context, message) => "[correlation-id = %s] %s".format(context(correlationId), message))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program
      .provideCustomMagicLayer(
        javaClock,
        DbSessionPool.layer,
        DbPostRepository.layer,
        AppConfig.layer.project(_.db),
        AppConfig.layer.project(_.auth0),
        JwtAuthService.jwkLayer,
        JwtAuthService.layer,
        logLayerLive,
        PostServiceLive.layer
      )
      .tapError(err => ZIO.effect(Option(err.getMessage).fold(err.printStackTrace())(println(_))))
      .exitCode

  private val program =
    for {
      _ <- Server.serve
    } yield ()
}
