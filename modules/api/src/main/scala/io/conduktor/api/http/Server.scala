package io.conduktor.api.http

import cats.syntax.all._
import io.conduktor.api.config.HttpConfig
import io.conduktor.api.http.posts.v1.PostRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware._
import org.http4s.server.{Router, Server}
import org.http4s.syntax.kleisli._

import zio.clock.Clock
import zio.interop.catz._
import zio.{Has, RIO, RLayer, ZManaged}

object Server {

  type ServerEnv = Clock with PostRoutes.Env with Has[HttpConfig]

  // In production you will want to restrict CORS config.
  val methodConfig: CORSConfig = CORSConfig.default

  // Starting the server (as a layer to simplify testing)
  val serve: ZManaged[ServerEnv, Throwable, Server] =
    ZManaged.runtime[ServerEnv].flatMap { implicit runtime =>
      for {
        conf   <- ZManaged.service[HttpConfig]
        server <-
          BlazeServerBuilder[RIO[PostRoutes.Env with Clock, *]](runtime.platform.executor.asEC)
            .bindHttp(conf.port, "0.0.0.0")
            .withHttpApp(CORS(Router("/" -> (RoutesInterpreter.appRoutes <+> RoutesInterpreter.swaggerRoute)).orNotFound, methodConfig))
            .resource
            .toManaged
      } yield server
    }

  val layer: RLayer[ServerEnv, Has[Server]] = serve.toLayer

}
