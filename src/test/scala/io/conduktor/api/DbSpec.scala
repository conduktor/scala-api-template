package io.conduktor.api

import io.conduktor.api.repository.db.DbSessionPool
import io.conduktor.api.repository.db.DbSessionPool.SessionTask
import skunk.codec.all.int4
import skunk.implicits.toStringOps
import skunk.{Query, Void}
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}
import zio.magic._
import zio.{TaskManaged, ZIO}

object DbSpec extends DefaultRunnableSpec {

  override def spec = suite("test technical db details")(
    testM("execute a simple query using a session from DbSessionPool") {

      val query: Query[Void, Int] = sql"SELECT 1".query(int4)

      (for {
        pool <- ZIO.service[TaskManaged[SessionTask]]
        res  <- pool.use { session =>
                  session.unique(query)
                }
      } yield (
        assert(res)(equalTo(1))
      )).provideCustomMagicLayer(DbSessionPool.layer, BootstrapPostgres.pgLayer)
    }
  )

}
