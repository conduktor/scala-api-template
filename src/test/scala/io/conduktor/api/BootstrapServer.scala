package io.conduktor.api

import io.conduktor.api.config._
import zio.ZLayer
import zio.Has
import io.conduktor.api.http.Server
import scala.util.Random

object BootstrapServer {

  val localAppConf = ZLayer.fromService[DBConfig, AppConfig] { dbConfig =>
    AppConfig(dbConfig, Auth0Config("foo", None), HttpConfig(Random.between(1024, 2024))) //FIXME random port generator
  }

  import zio.magic._
  val localStack = ZLayer.fromSomeMagic[zio.ZEnv, zio.ZEnv with Has[AppConfig]](BootstrapPostgres.pgLayer >>> localAppConf)

  val localServer: ZLayer[zio.ZEnv, Throwable, Has[Server.Server]] = localStack >>> ApiTemplateApp.apiRuntimelayers >>> Server.layer
}
