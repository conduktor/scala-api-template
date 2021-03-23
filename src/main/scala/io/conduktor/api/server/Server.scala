package io.conduktor.api.server

import cats.syntax.all._
import io.conduktor.api.server.endpoints.PostRoutes
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
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


  // Starting the server
  val serve: ZIO[ZEnv with PostRoutes.Env, Throwable, Unit] =
    ZIO.runtime[ZEnv with PostRoutes.Env].flatMap { implicit runtime => // This is needed to derive cats-effect instances for that are needed by http4s
      BlazeServerBuilder[RIO[PostRoutes.Env with Clock, *]](runtime.platform.executor.asEC)
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> (RoutesInterpreter.routes <+> new SwaggerHttp4s(yaml).routes)).orNotFound)
        .serve
        .compile
        .drain
    }

}
