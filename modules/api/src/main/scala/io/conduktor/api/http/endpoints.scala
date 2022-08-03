package io.conduktor.api.http

import io.conduktor.api.auth.AuthService
import io.conduktor.api.auth.AuthService.AuthToken
import io.conduktor.api.model.User
import sttp.model.StatusCode
import sttp.tapir.PublicEndpoint
import sttp.tapir.codec.newtype._
import sttp.tapir.codec.refined._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._

import zio.{Has, ZIO}

object endpoints {

  /**
   * Public endpoint
   */
  val baseEndpoint: PublicEndpoint[Unit, ErrorInfo, Unit, Any] = endpoint.errorOut(
    oneOf[ErrorInfo](
      oneOfVariant(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
      oneOfVariant(StatusCode.BadRequest, jsonBody[BadRequest].description("bad request")),
      oneOfVariant(StatusCode.Unauthorized, emptyOutputAs(Unauthorized)),
      oneOfVariant(StatusCode.NoContent, emptyOutputAs(NoContent)),
      oneOfVariant(StatusCode.Forbidden, emptyOutputAs(Forbidden)),
      oneOfVariant(StatusCode.Conflict, jsonBody[Conflict].description("conflict")),
      oneOfVariant(StatusCode.InternalServerError, jsonBody[ServerError].description("server error"))
      // default is somehow broken since tapir 0.18, this leads to a ClassCastException on error
      // oneOfDefaultMapping(jsonBody[ServerError].description("unknown"))
    )
  )

  /**
   * User need a valid JWT and to pass the validation
   *
   * ex: assert that user is a member of your admin domain
   */
  def restrictedEndpoint(
    userFilter: User => Boolean
  ): ZPartialServerEndpoint[Has[AuthService], Option[AuthToken], User, Unit, ErrorInfo, Unit, Any] =
    baseEndpoint.securityIn(auth.bearer[Option[AuthToken]]()).zServerSecurityLogic { tokenOpt =>
      tokenOpt
        .map(token =>
          AuthService
            .auth(token)
            .orElseFail[ErrorInfo](Unauthorized)
            .filterOrFail(userFilter)(Forbidden)
        )
        .getOrElse(ZIO.fail(Unauthorized))
    }

  /**
   * Any user with a valid JWT can access this endpoint
   */
  val secureEndpoint: ZPartialServerEndpoint[Has[AuthService], Option[AuthToken], User, Unit, ErrorInfo, Unit, Any] =
    restrictedEndpoint(_ => true)

}
