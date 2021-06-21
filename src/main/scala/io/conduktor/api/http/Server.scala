package io.conduktor.api.http

import cats.syntax.all._
import io.conduktor.api.http.v1.PostRoutes
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware._
import org.http4s.syntax.kleisli._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import sttp.tapir.ztapir._
import zio.clock.Clock
import zio.interop.catz._
import zio.{Has, RIO, ZLayer, ZManaged, system}

import scala.concurrent.duration._

/*
  Interpret Tapir endpoints as Http4s routes
 */
object RoutesInterpreter {
  val routes: HttpRoutes[RIO[PostRoutes.Env with Clock, *]] =
    ZHttp4sServerInterpreter
      .from(
        PostRoutes.Endpoints.all.map(_.widen[PostRoutes.Env])
      )
      .toRoutes
}

object Server {

  type Server = org.http4s.server.Server[RIO[PostRoutes.Env with Clock, *]]

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
  private val serve: ZManaged[system.System with Clock with PostRoutes.Env, Throwable, Server] =
    ZManaged.runtime[system.System with Clock with PostRoutes.Env].flatMap {
      implicit runtime => // This is needed to derive cats-effect instances for that are needed by http4s
        for {
        //FIXME: how do you combine ZManaged and reading the env?
          //port   <- zio.system.env("PORT").map(_.flatMap(_.toIntOption).getOrElse(8080))
          server <-
            BlazeServerBuilder[RIO[PostRoutes.Env with Clock, *]](runtime.platform.executor.asEC)
            //put back the right port
              .bindHttp(0, "0.0.0.0")
              .withHttpApp(CORS(Router("/v1" -> (RoutesInterpreter.routes <+> new SwaggerHttp4s(yaml).routes)).orNotFound, methodConfig))
              .resource
              .toManaged
        } yield server
    }

  val layer: ZLayer[system.System with Clock with PostRoutes.Env, Throwable, Has[Server]] = serve.toLayer
}
