package io.conduktor.api

import eu.timepit.refined.auto._
import io.conduktor.api.auth.AuthService
import io.conduktor.api.auth.AuthService.AuthToken
import io.conduktor.api.config._
import io.conduktor.api.db.{EmbeddedPostgres, FlywayDatabaseMigrationService}
import io.conduktor.api.model.User
import io.conduktor.primitives.types

import zio.{Has, Task, TaskLayer, ULayer, ZLayer}

object ServerTestLayers {

  val localDB: TaskLayer[Has[DBConfig]] = EmbeddedPostgres.pgLayer.tap { conf =>
    FlywayDatabaseMigrationService.layer.build.useNow
      .flatMap(_.get.migrate())
      .provide(conf)
  }

  val dummyAuth: ULayer[Has[AuthService]] = ZLayer.succeed((_: AuthToken) => Task.succeed(User(types.Email("john.doe@conduktor.io"))))

  val randomPortHttpConfig: ULayer[Has[HttpConfig]] = ZLayer.succeed(HttpConfig(0))

}
