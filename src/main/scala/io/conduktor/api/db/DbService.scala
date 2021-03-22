package io.conduktor.api.db

import io.conduktor.api.config.DBConfig
import skunk._

import zio._

object DbSessionPool {
  type ZSession = ZManaged[Any, Throwable, Session[Task]]
  type DbSessionPool = Has[DbSessionPool.Service]

  trait Service {
    def pool: ZManaged[Any, Throwable, ZSession]
  }
  def pool : ZManaged[DbSessionPool.Service with Has[DBConfig], Throwable, ZSession] =
    ZManaged.accessManaged(_.pool)

  val live: ZLayer[Has[DBConfig], Throwable, Has[DbSessionPool.Service]] = ZLayer.fromService {
    conf => new Service {
      println("DbSessionPool live")
      override def pool: ZManaged[Any, Throwable, ZSession] = {

        import natchez.Trace.Implicits.noop
        import zio.interop.catz._

        ZManaged.runtime[Any].flatMap { implicit runtime =>
          println("DbSessionPool live inner")

          for {
            poolResource <- Session
              .pooled[Task](
                host = conf.host,
                port = conf.port,
                user = conf.user,
                database = conf.database,
                password = conf.password,
                max = conf.maxPoolSize,
                strategy = Strategy.SearchPath,
                parameters = Session.DefaultConnectionParameters ++ conf.additionalSourceProperties
              ).toManagedZIO
          } yield poolResource.toManagedZIO
        }
      }
    }
  }
}

object DbSession {
  type DbSession = Has[DbSession.Service]

  trait Service {
    def session: ZManaged[Any, Throwable, Session[Task]]
  }

  def session: ZManaged[DbSession.Service, Throwable, Session[Task]] =
    ZManaged.accessManaged(_.session)

  val live: ZLayer[Has[DbSessionPool.Service], Throwable, Has[DbSession.Service]] =
    ZLayer.fromServiceManaged { poolService =>
      println("DbSession live")

      poolService.pool.map { managedSession =>
        new Service {
          println("DbSession live inner")
          override def session: ZManaged[Any, Throwable, Session[Task]] = managedSession.map { a =>
            println("session inner")
            a
          }
        }
      }
    }
}
