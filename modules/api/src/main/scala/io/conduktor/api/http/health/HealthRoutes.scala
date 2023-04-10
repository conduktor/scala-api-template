package io.conduktor.api.http.health

import io.conduktor.api.http.endpoints.baseEndpoint
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir._

import zio.ZIO

object HealthRoutes {

  object Endpoints {
    def healthEndpoint: ZServerEndpoint[Any, ZioStreams] = baseEndpoint.get
      .in("health")
      .out(emptyOutput)
      .zServerLogic(_ => ZIO.unit)

    def helloWorldEndpoint: ZServerEndpoint[Any, ZioStreams] = baseEndpoint.get
      .in("hello-world")
      .out(stringBody)
      .zServerLogic(_ => ZIO.effectTotal("hello world !"))

    val all: List[ZServerEndpoint[Any, ZioStreams]] = List(
      healthEndpoint,
      helloWorldEndpoint
    )
  }

}
