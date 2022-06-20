package io.conduktor.api.http

import io.conduktor.api.http.health.HealthRoutes
import org.http4s.HttpRoutes
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir._

import zio.RIO
import zio.blocking.Blocking
import zio.clock.Clock

/*
  Interpret Tapir endpoints as Http4s routes
 */
object RoutesInterpreter {

  type Env = posts.v1.PostRoutes.Env

  val endpoints: List[ZServerEndpoint[Env, ZioStreams]] =
    HealthRoutes.Endpoints.all.map(_.widen[Env]) ++
      posts.v1.PostRoutes.Endpoints.all

  val swaggerRoute: HttpRoutes[RIO[Env with Clock with Blocking, *]] =
    ZHttp4sServerInterpreter[Env]()
      .from(
        SwaggerInterpreter()
          .fromServerEndpoints[RIO[Env, *]](endpoints, "Template API", "1.0")
      )
      .toRoutes

  val appRoutes: HttpRoutes[RIO[Env with Clock with Blocking, *]] =
    ZHttp4sServerInterpreter[Env]()
      .from(endpoints)
      .toRoutes
}
