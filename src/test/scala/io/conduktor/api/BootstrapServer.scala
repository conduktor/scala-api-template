package io.conduktor.api

import eu.timepit.refined.auto._
import io.conduktor.api.config._
import zio.{Has, RLayer, Task, TaskLayer, ZLayer}
import io.conduktor.api.http.Server
import io.conduktor.api.auth.AuthService
import io.conduktor.api.auth.User
import io.conduktor.api.http.Server.Server
import io.conduktor.api.http.v1.PostRoutes
import io.conduktor.api.migration.FlywayDatabaseMigrationService
import io.conduktor.api.repository.db.DbPostRepository
import io.conduktor.api.service.PostService

object BootstrapServer {


  val localDB: TaskLayer[Has[DBConfig]] = BootstrapPostgres.pgLayer.tap { conf =>
    FlywayDatabaseMigrationService.layer.build.useNow
      .flatMap(_.get.migrate())
      .provide(conf)
  }

  val dummyAuth = ZLayer.succeed(new AuthService {
    override def auth(token: String): Task[User] = Task.succeed(User(types.UserName("John Doe")))
  })

  val localServer: RLayer[zio.ZEnv, Has[Server.Server]] =
    (localDB ++ ZLayer.identity[zio.ZEnv]) >>>
      (ZLayer.identity[zio.ZEnv] ++ (ApiTemplateApp.dbLayer >>> ApiTemplateApp.serviceLayer) ++ dummyAuth ++ ZLayer.succeed(HttpConfig(0))) >>>
      Server.layer

}
