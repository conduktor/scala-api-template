package io.conduktor.api.db

import io.conduktor.api.db.DbSessionPool.SessionTask
import io.conduktor.api.repository.PostRepository
import skunk.implicits.toStringOps
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec}
import zio.{Has, TaskManaged}

object DbRepositorySpec extends DefaultRunnableSpec {

  private def initTables(x: Has[TaskManaged[SessionTask]]) =
    x.get.use { session =>
      session.execute(sql"""
CREATE TABLE "post" (
"id" UUID NOT NULL,
"title" TEXT NOT NULL,
"published" BOOLEAN NOT NULL DEFAULT false,
"author" TEXT NOT NULL,
"content" TEXT NOT NULL,
"created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

PRIMARY KEY ("id")
)""".command)
    }

  val repoLayer = (EmbeddedPostgres.pgLayer >>> DbSessionPool.layer.tap(initTables) >>> PostRepository.Pool.live).orDie

  override def spec: ZSpec[TestEnvironment, Any] = RepositorySpec.spec(repositoryType = "database").provideCustomLayer(repoLayer)

}
