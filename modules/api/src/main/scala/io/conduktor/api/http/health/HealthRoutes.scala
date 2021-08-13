package io.conduktor.api.http.health

import io.conduktor.api.http.ErrorInfo
import io.conduktor.api.http.endpoints.baseEndpoint
import sttp.tapir.ztapir._

import zio.ZIO

object HealthRoutes {

  object Endpoints {
    def healthEndpoint: ZServerEndpoint[Any, Unit, ErrorInfo, Unit] = baseEndpoint.get
      .in("health")
      .out(emptyOutput)
      .zServerLogic(_ => ZIO.unit)

    def helloWorldEndpoint: ZServerEndpoint[Any, Unit, ErrorInfo, String] = baseEndpoint.get
      .in("hello-world")
      .out(stringBody)
      .zServerLogic(_ => ZIO.effectTotal("hello world !"))

    val all: List[ZServerEndpoint[Any, _, _, _]] = List(
      healthEndpoint,
      helloWorldEndpoint
    )
  }

}
