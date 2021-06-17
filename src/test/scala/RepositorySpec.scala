import com.opentable.db.postgres.embedded.EmbeddedPostgres
import eu.timepit.refined.auto._
import io.conduktor.api.auth.User
import io.conduktor.api.config.DBConfig
import io.conduktor.api.model.Post
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.repository.db.DbSessionPool.SessionTask
import io.conduktor.api.repository.db.{DbPostRepository, DbSessionPool}
import io.conduktor.api.types.UserName
import skunk.implicits.toStringOps
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, ZSpec, assert}
import zio.{Has, Task, TaskManaged, ZIO, ZLayer}

import java.util.UUID

object DbRepositorySpec extends RepositorySpec {

  val pg       = EmbeddedPostgres.builder().start()
  val dbConfig = DBConfig(
    user = "postgres",
    password = None,
    host = "localhost",
    port = pg.getPort,
    database = "postgres",
    maxPoolSize = 5,
    gcpInstance = None,
    ssl = false
  )

  val dbLayer = ZLayer.succeed(dbConfig) >>> DbSessionPool.layer
  val repository = dbLayer.tap(initTables) >>> DbPostRepository.layer

  private def initTables(x: Has[TaskManaged[SessionTask]]) = {
    for {
      _ <- x.get.use {

        session =>
          session.execute(
            sql"""
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
    } yield ()
  }
}

object MemoryRepositorySpec extends RepositorySpec {

  class InMemoryPostRepository extends PostRepository {

    private val db = collection.mutable.Map[UUID, Post]()

    override def createPost(id: UUID, title: Post.Title, author: UserName, content: Post.Content): Task[Post] = Task {
      db(id) = Post(
        id = id,
        title = Post.Title("hello"),
        author = User(UserName("bob")),
        published = false,
        content = Post.Content("testing")
      )
      db(id)
    }

    override def findPostByTitle(title: Post.Title): Task[Option[Post]] = Task {
      db.values.find(_.title == title)
    }

    override def deletePost(id: UUID): Task[Unit] = Task {
      db.remove(id)
      ()
    }

    override def findPostById(id: UUID): Task[Post] = Task {
      db(id)
    }

    override def allPosts: ZIO[Any, Throwable, List[Post]] = Task {
      db.values.toList
    }
  }

  val repository = ZLayer.succeed(new InMemoryPostRepository)

}

trait RepositorySpec extends DefaultRunnableSpec {

  def repository: ZLayer[zio.ZEnv, Throwable, Has[PostRepository]]

  override def spec: ZSpec[zio.test.environment.TestEnvironment, Any] =
    suite("test the behavior of the repository")(
      testM("a created post can be retrieved by id") {
        //FIXME: inject database schema properly
        //FIXME: extract uuid into a zlayer
        val id = UUID.fromString("08d7d61d-7e69-44b7-b66e-4203c192ff82")
        (for {
          repo   <- ZIO.service[PostRepository]
          _      <- repo.createPost(
                      id = id,
                      title = Post.Title("hello"),
                      author = UserName("bob"),
                      content = Post.Content("testing")
                    )
          actual <- repo.findPostById(id)
        } yield assert(actual)(
          equalTo(
            Post(id = id, title = Post.Title("hello"), author = User(UserName("bob")), published = false, content = Post.Content("testing"))
          )
        )).provideCustomLayer(repository)
      }
    )
}
