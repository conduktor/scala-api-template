package io.conduktor.api.db

import cats.effect.std.{Console => CatsConsole}
import io.conduktor.api.config.DBConfig
import natchez.Trace.Implicits.noop
import skunk.{SSL, Session, Strategy}

import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{Has, Task, TaskManaged, ZLayer, ZManaged}

object DbSessionPool {

  type SessionTask = Session[Task]

  val layer: ZLayer[Has[DBConfig], Throwable, Has[TaskManaged[SessionTask]]] = {
    implicit val console: CatsConsole[Task] = CatsConsole.make[Task]

    (for {
      conf <- ZManaged.service[DBConfig]
      pool <- Session
                .pooled[Task](
                  host = conf.host,
                  port = conf.port,
                  user = conf.user,
                  database = conf.database,
                  password = conf.password.map(_.unwrapValue),
                  max = conf.maxPoolSize,
                  strategy = Strategy.SearchPath,
                  ssl = if (conf.ssl) SSL.Trusted else SSL.None
                )
                .toManagedZIO
    } yield pool.toManagedZIO).toLayer
  }

}
