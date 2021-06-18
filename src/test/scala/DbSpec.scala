import io.conduktor.api.repository.db.DbSessionPool.SessionTask
import skunk._
import skunk.implicits.toStringOps
import zio._
import zio.magic._
import zio.test._
import zio.test.Assertion._
import skunk.codec.all._

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
      )).provideCustomMagicLayer(BootstrapPostgres.dbLayer)
    }
  )

}
