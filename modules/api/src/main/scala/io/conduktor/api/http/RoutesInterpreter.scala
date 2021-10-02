package io.conduktor.api.http

import io.conduktor.api.http.health.HealthRoutes
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import sttp.tapir.ztapir._

import zio.RIO
import zio.clock.Clock
import zio.interop.catz._

/*
  Interpret Tapir endpoints as Http4s routes
 */
object RoutesInterpreter {

  type Env = posts.v1.PostRoutes.Env

  val endpoints: List[ZServerEndpoint[Env, _, _, _]] =
    HealthRoutes.Endpoints.all.map(_.widen[Env]) ++
      posts.v1.PostRoutes.Endpoints.all

  private lazy val allDocsYaml: String               = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._
    OpenAPIDocsInterpreter().serverEndpointsToOpenAPI(endpoints, "Template API", "1.0").toYaml
  }

  val swaggerRoute: HttpRoutes[RIO[Env with Clock, *]] = new SwaggerHttp4s(yaml = allDocsYaml, contextPath = List("docs")).routes

  val appRoutes: HttpRoutes[RIO[Env with Clock, *]] =
    ZHttp4sServerInterpreter()
      .from(endpoints)
      .toRoutes
}
