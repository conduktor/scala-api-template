package io.conduktor.api

import io.conduktor.api.auth.User
import io.conduktor.api.model.Post
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.types.UserName
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec}
import zio.{Has, Task, ULayer, ZIO}

import java.util.UUID

object MemoryRepositorySpec extends DefaultRunnableSpec {

  class InMemoryPostRepository extends PostRepository {

    private val db = collection.mutable.Map[UUID, Post]()

    override def createPost(id: UUID, title: Post.Title, author: UserName, content: Post.Content): Task[Post] = Task {
      db(id) = Post(
        id = Post.Id(id),
        title = title,
        author = User(author),
        published = false,
        content = content
      )
      db(id)
    }

    override def findPostByTitle(title: Post.Title): Task[Option[Post]] =
      Task {
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

  val testLayer: ULayer[Has[PostRepository]] = (() => new InMemoryPostRepository).toLayer

  override def spec: ZSpec[TestEnvironment, Any] = RepositorySpec.spec(repositoryType = "memory").provideCustomLayer(testLayer)

}
