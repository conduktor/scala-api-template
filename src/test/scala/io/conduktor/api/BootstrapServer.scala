package io.conduktor.api

import eu.timepit.refined.auto._
import io.conduktor.api.config._
import zio.ZLayer
import zio.Has
import io.conduktor.api.http.Server
import io.conduktor.api.auth.AuthService
import io.conduktor.api.auth.User
import io.conduktor.api.migration.FlywayDatabaseMigrationService
import zio.Task

object BootstrapServer {

  import zio.magic._

  val localAppConf = ZLayer.fromService[DBConfig, AppConfig] { dbConfig =>
    AppConfig(dbConfig, Auth0Config("foo", None), HttpConfig(0))
  }

  val localDB = BootstrapPostgres.pgLayer.tap { conf =>
    FlywayDatabaseMigrationService.layer.build.useNow
      .flatMap(_.get.migrate())
      .provide(conf)
  }

  val globalEnv = ZLayer.fromSomeMagic[zio.ZEnv, zio.ZEnv with Has[AppConfig]](localDB >>> localAppConf)

  val dummyAuth = ZLayer.succeed(new AuthService {
    override def auth(token: String): Task[User] = Task.succeed(User(types.UserName("John Doe")))
  })

  val localServer: ZLayer[zio.ZEnv, Throwable, Has[Server.Server]] =
    globalEnv >>> (ApiTemplateApp.apiRuntimelayers ++ dummyAuth) >>> Server.layer

}
