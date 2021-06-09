package io.conduktor.api.server

import scala.concurrent.duration._

import cats.syntax.all._
import io.conduktor.api.server.endpoints.PostRoutes
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware._
import org.http4s.syntax.kleisli._
import sttp.tapir.swagger.http4s.SwaggerHttp4s

import zio.clock.Clock
import zio.interop.catz._
import zio.{RIO, ZEnv, ZIO}

object Server {

  private lazy val yaml: String = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._
    val endpoints = PostRoutes.Endpoints.all.map(_.endpoint)
    OpenAPIDocsInterpreter.toOpenAPI(endpoints, "Example API", "1.0").toYaml
  }

  // In production you would want to restrict CORS config.
  // Allowing * as we aren't deploying to a specific domain
  val methodConfig: CORSConfig = CORSConfig(anyOrigin = true, anyMethod = true, allowCredentials = true, maxAge = 1.day.toSeconds)

  // Starting the server
  val serve: ZIO[ZEnv with PostRoutes.Env, Throwable, Unit] =
    ZIO.runtime[ZEnv with PostRoutes.Env].flatMap {
      implicit runtime => // This is needed to derive cats-effect instances for that are needed by http4s
        for {
          port   <- zio.system.env("PORT").map(_.flatMap(_.toIntOption).getOrElse(8080))
          server <-
            BlazeServerBuilder[RIO[PostRoutes.Env with Clock, *]](runtime.platform.executor.asEC)
              .bindHttp(port, "0.0.0.0")
              .withHttpApp(CORS(Router("/" -> (RoutesInterpreter.routes <+> new SwaggerHttp4s(yaml).routes)).orNotFound, methodConfig))
              .serve
              .compile
              .drain
        } yield server
    }
}
