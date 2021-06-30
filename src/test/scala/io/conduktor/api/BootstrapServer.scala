package io.conduktor.api

import eu.timepit.refined.auto._
import io.conduktor.api.config._
import zio.ZLayer
import zio.Has
import io.conduktor.api.http.Server
import io.conduktor.api.auth.AuthService
import io.conduktor.api.auth.User
import io.conduktor.api.http.Server.Server
import io.conduktor.api.http.v1.PostRoutes
import io.conduktor.api.migration.FlywayDatabaseMigrationService
import io.conduktor.api.repository.db.DbPostRepository
import io.conduktor.api.service.PostService
import zio.Task

object BootstrapServer {

  val localAppConf = ZLayer.fromService[DBConfig, AppConfig] { dbConfig =>
    AppConfig(dbConfig, Auth0Config("foo", None), HttpConfig(0))
  }

  val localDB = BootstrapPostgres.pgLayer.tap { conf =>
    FlywayDatabaseMigrationService.layer.build.useNow
      .flatMap(_.get.migrate())
      .provide(conf)
  }

  private val appConfigUsingLocalDb: ZLayer[zio.ZEnv, Throwable, Has[AppConfig]] = localDB >>> localAppConf

  val dummyAuth = ZLayer.succeed(new AuthService {
    override def auth(token: String): Task[User] = Task.succeed(User(types.UserName("John Doe")))
  })

  val localServer: ZLayer[zio.ZEnv, Throwable, Has[Server.Server]] =
    (appConfigUsingLocalDb ++ ZLayer.identity[zio.ZEnv]) >>>
      (ZLayer.identity[zio.ZEnv] ++ (ApiTemplateApp.dbLayer >>> ApiTemplateApp.serviceLayer) ++ ApiTemplateApp.httpConfig ++ dummyAuth) >>>
      Server.layer

}
