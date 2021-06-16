package io.conduktor.api.db

import io.conduktor.api.config.DBConfig
import skunk._
import zio.{Task, _}
import zio.interop.catz._

import natchez.Trace.Implicits.noop

object DbSessionPool {

  type SessionTask = Session[Task]

  val layer: ZLayer[Has[DBConfig], Throwable, Has[TaskManaged[SessionTask]]] =
    ZManaged
      .runtime[Any]
      .flatMap { implicit r: Runtime[Any] =>
        for {
          conf    <- ZManaged.service[DBConfig]
          pool    <- Session
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
