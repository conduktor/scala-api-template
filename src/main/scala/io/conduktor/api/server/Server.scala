package io.conduktor.api.server

import cats.syntax.all._
import io.conduktor.api.server.endpoints.PostRoutes
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import zio.clock.Clock
import zio.interop.catz._
import io.circe.generic.auto._
import zio.{RIO, ZEnv, ZIO}
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import zio._
import zio.clock.Clock
import zio.interop.catz._

object Server {

  private val yaml2: String = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._
    OpenAPIDocsInterpreter.serverEndpointsToOpenAPI(List(PostRoutes.Endpoints.createPostEndpoint), "Example API", "1.0").toYaml
  }

  val serve2: ZIO[ZEnv with PostRoutes.Env, Throwable, Unit] =
    ZIO.runtime[ZEnv with PostRoutes.Env].flatMap { implicit runtime =>
      BlazeServerBuilder[RIO[PostRoutes.Env with Clock, *]](runtime.platform.executor.asEC)
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> (RoutesInterpreter.routes <+> new SwaggerHttp4s(yaml).routes)).orNotFound)
        .serve
        .compile
        .drain
    }


  case class Pet(species: String, url: String)

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: ZEndpoint[Int, String, Pet] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpRoutes[RIO[Clock, *]] = ZHttp4sServerInterpreter
    .from(petEndpoint) { petId =>
      if (petId == 35) {
        UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
      } else {
        IO.fail("Unknown pet id")
      }
    }
    .toRoutes

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[Any, Int, String, Pet] = petEndpoint.zServerLogic { petId =>
    if (petId == 35) {
      UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      IO.fail("Unknown pet id")
    }
  }
  val petServerRoutes: HttpRoutes[RIO[Clock, *]] = ZHttp4sServerInterpreter.from(petServerEndpoint).toRoutes

  //

  val yaml: String = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._
    OpenAPIDocsInterpreter.toOpenAPI(petEndpoint, "Our pets", "1.0").toYaml
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
