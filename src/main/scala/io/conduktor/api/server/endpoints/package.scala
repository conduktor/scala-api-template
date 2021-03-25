package io.conduktor.api.server


import io.circe.generic.auto._
import io.conduktor.api.auth.UserAuthenticationLayer._
import sttp.model.StatusCode
import sttp.tapir.Endpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.ztapir._


package object endpoints {


  // here we are defining an error output, but the same can be done for regular outputs
  val baseEndpoint: Endpoint[Unit, ErrorInfo, Unit, Any] = endpoint.errorOut(
    oneOf[ErrorInfo](
      statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
      statusMapping(StatusCode.Unauthorized, emptyOutput.map(_ => Unauthorized)(_ => ())),
      statusMapping(StatusCode.NoContent, emptyOutput.map(_ => NoContent)(_ => ())),
      statusDefaultMapping(jsonBody[ServerError].description("unknown"))
    )
  )

  val secureEndpoint: ZPartialServerEndpoint[AuthService, User, Unit, ErrorInfo, Unit] = baseEndpoint
    .in(header[String]("Authorization"))
    .zServerLogicForCurrent(AuthService.auth(_).mapError(e => {
      println("AUTH ERROR" + e.getMessage)
      e.printStackTrace()
      Unauthorized
    }))

}
