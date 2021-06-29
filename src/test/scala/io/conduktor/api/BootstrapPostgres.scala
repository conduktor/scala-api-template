package io.conduktor.api

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import io.conduktor.api.config.DBConfig
import zio.{Task, ZLayer, ZManaged}

object BootstrapPostgres {

  val pgLayer = ZLayer.fromManaged(ZManaged.make(Task(EmbeddedPostgres.start()))(pg => Task(pg.close()).orDie).map { pg =>
    DBConfig(
      user = "postgres",
      password = None,
      host = "localhost",
      port = pg.getPort,
      database = "postgres",
      maxPoolSize = 5,
      gcpInstance = None,
      ssl = false
    )
  })
}
