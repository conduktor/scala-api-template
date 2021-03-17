package io.conduktor.api

import eu.timepit.refined.auto._
import io.conduktor.api.auth.{AuthService, User}
import io.conduktor.api.config._
import io.conduktor.api.migration.FlywayDatabaseMigrationService
import zio.{Has, Task, TaskLayer, ULayer, ZLayer}

object ServerTestLayers {


  val localDB: TaskLayer[Has[DBConfig]] = BootstrapPostgres.pgLayer.tap { conf =>
    FlywayDatabaseMigrationService.layer.build.useNow
      .flatMap(_.get.migrate())
      .provide(conf)
  }

  val dummyAuth: ULayer[Has[AuthService]] = ZLayer.succeed((_: String) => Task.succeed(User(types.UserName("John Doe"))))

  val randomPortHttpConfig: ULayer[Has[HttpConfig]] = ZLayer.succeed(HttpConfig(0))

}
