import eu.timepit.refined.auto._
import io.conduktor.api.auth.User
import io.conduktor.api.model.Post
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.repository.db.{DbPostRepository, DbSessionPool}
import io.conduktor.api.repository.db.DbSessionPool.SessionTask
import io.conduktor.api.types.UserName
import skunk.implicits.toStringOps
import zio.test.Assertion.equalTo
import zio.test.{ZSpec, _}
import zio.test.environment.TestEnvironment
import zio.{Has, Task, TaskManaged, ULayer, ZIO, ZLayer}

import java.util.UUID

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

  val repoLayer = (BootstrapPostgres.pgLayer >>> DbSessionPool.layer.tap(initTables) >>> DbPostRepository.layer).orDie

  override def spec: ZSpec[TestEnvironment, Any] = RepositorySpec.spec(repositoryType = "database").provideCustomLayer(repoLayer)

}

object MemoryRepositorySpec extends DefaultRunnableSpec {

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

  val testLayer: ULayer[Has[PostRepository]] = ZLayer.succeed[PostRepository](new InMemoryPostRepository)

  override def spec: ZSpec[TestEnvironment, Any] = RepositorySpec.spec(repositoryType = "memory").provideCustomLayer(testLayer)

}

object RepositorySpec {

  def spec(repositoryType: String): ZSpec[TestEnvironment with Has[PostRepository], Any] =
    suite(s"test the behavior of the repository $repositoryType")(
      testM(s"a created post can be retrieved by id") {
        //FIXME: inject database schema properly
        //FIXME: extract uuid into a zlayer
        val id = UUID.fromString("08d7d61d-7e69-44b7-b66e-4203c192ff82")
        for {
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
        )
      }
    )
}
