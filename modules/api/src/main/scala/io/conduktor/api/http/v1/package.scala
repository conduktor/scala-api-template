package io.conduktor.api.http

import io.conduktor.api.auth._
import sttp.model.StatusCode
import sttp.tapir.Endpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.ztapir._
import zio.Has

package object v1 {

  // here we are defining an error output, but the same can be done for regular outputs
  val baseEndpoint: Endpoint[Unit, ErrorInfo, Unit, Any] =
    endpoint.errorOut(
      oneOf[ErrorInfo](
        statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
        statusMapping(StatusCode.Unauthorized, emptyOutput.map(_ => Unauthorized)(_ => ())),
        statusMapping(StatusCode.NoContent, emptyOutput.map(_ => NoContent)(_ => ())),
        statusMapping(StatusCode.Conflict, jsonBody[Conflict].description("conflict")),
        statusDefaultMapping(jsonBody[ServerError].description("unknown"))
      )
    )

  val secureEndpoint: ZPartialServerEndpoint[Has[AuthService], User, Unit, ErrorInfo, Unit] =
    baseEndpoint
      .in(header[String]("Authorization")) // TODO that return 400 if no auth header
      .zServerLogicForCurrent(AuthService.auth(_).orElseFail(Unauthorized))

}
