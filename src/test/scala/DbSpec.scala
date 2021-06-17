import com.opentable.db.postgres.embedded.EmbeddedPostgres
import io.conduktor.api.config.DBConfig
import io.conduktor.api.repository.db.DbSessionPool
import io.conduktor.api.repository.db.DbSessionPool.SessionTask
import skunk._
import skunk.implicits.toStringOps
import zio._
import zio.magic._
import zio.test._
import zio.test.Assertion._
import skunk.codec.all._

object DbSpec extends DefaultRunnableSpec {

  val pg = EmbeddedPostgres.builder().start()
  val dbConfig = DBConfig(user = "postgres",
          password = None,
          host = "localhost",
          port = pg.getPort,
          database = "postgres",
          maxPoolSize = 5,
          gcpInstance = None,
          ssl = false)

  override def spec = suite("test technical db details")(

    testM("execute a simple query using a session from DbSessionPool") {

      val query : Query[Void, Int] = sql"SELECT 1".query(int4)

      (for {
        pool <- ZIO.service[TaskManaged[SessionTask]]
        res <- pool.use {
          session => session.unique(query)
        }
      } yield (
      assert(res)(equalTo(1))
      )).provideCustomMagicLayer(
        ZLayer.succeed(dbConfig),
        DbSessionPool.layer
      )
    }

  )

}
