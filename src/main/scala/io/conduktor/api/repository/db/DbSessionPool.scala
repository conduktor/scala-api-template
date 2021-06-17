package io.conduktor.api.repository.db

import io.conduktor.api.config.DBConfig
import natchez.Trace.Implicits.noop
import skunk.{SSL, Session, Strategy}
import zio.interop.catz._
import zio.{Has, Runtime, Task, TaskManaged, ZLayer, ZManaged}

object DbSessionPool {

  type SessionTask = Session[Task]

  val layer: ZLayer[Has[DBConfig], Throwable, Has[TaskManaged[SessionTask]]] =
    ZManaged
      .runtime[Any]
      .flatMap { implicit r: Runtime[Any] =>
        for {
          conf <- ZManaged.service[DBConfig]
          pool <- Session
                    .pooled[Task](
                      host = conf.host,
                      port = conf.port,
                      user = conf.user,
                      database = conf.database,
                      password = conf.password,
                      max = conf.maxPoolSize,
                      strategy = Strategy.SearchPath,
                      ssl = if (conf.ssl) SSL.Trusted else SSL.None
                    )
                    .toManaged
        } yield pool.toManaged
      }
      .toLayer

}
