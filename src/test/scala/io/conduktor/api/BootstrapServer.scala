package io.conduktor.api

import io.conduktor.api.config._
import zio.ZLayer
import zio.Has
import io.conduktor.api.http.Server

object BootstrapServer {

  val localAppConf = ZLayer.fromService[DBConfig, AppConfig] { dbConfig =>
    AppConfig(dbConfig, Auth0Config("foo", None), HttpConfig(0))
  }

  import zio.magic._
  val localStack = ZLayer.fromSomeMagic[zio.ZEnv, zio.ZEnv with Has[AppConfig]](BootstrapPostgres.pgLayer >>> localAppConf)

  val localServer: ZLayer[zio.ZEnv, Throwable, Has[Server.Server]] = localStack >>> ApiTemplateApp.apiRuntimelayers >>> Server.layer
}
