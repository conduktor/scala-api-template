package io.conduktor.api.server

import io.conduktor.api.server.endpoints.PostRoutes
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir._

import zio.RIO
import zio.clock.Clock
import zio.interop.catz._


/*
  Interpret Tapir endpoints as Http4s routes
 */
object RoutesInterpreter {
  val routes: HttpRoutes[RIO[PostRoutes.Env with Clock, *]] =
    ZHttp4sServerInterpreter.from(
      PostRoutes.Endpoints.all.map(_.widen[PostRoutes.Env])
   ).toRoutes
}