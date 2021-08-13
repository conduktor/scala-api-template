package io.conduktor.api.db

import com.opentable.db.postgres.embedded.{EmbeddedPostgres => Postgres}
import io.conduktor.api.config.DBConfig

import zio.{Has, Task, ZLayer, ZManaged}

object EmbeddedPostgres {

  val pgLayer: ZLayer[Any, Throwable, Has[DBConfig]] =
    ZLayer.fromManaged(ZManaged.make(Task(Postgres.start()))(pg => Task(pg.close()).orDie).map { pg =>
      DBConfig(
        user = "postgres",
        password = None,
        host = "localhost",
        port = pg.getPort,
        database = "postgres",
        maxPoolSize = 5,
        gcpInstance = None,
        ssl = false,
        migrate = false,
        baselineVersion = None
      )
    })
}
