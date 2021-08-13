package io.conduktor.api.db

import io.conduktor.api.model.Post
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.repository.PostRepository.Error
import io.conduktor.primitives.types.UserName
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec}
import zio.{Has, IO, UIO, ULayer, ZManaged}

import java.util.UUID

object MemoryRepositorySpec extends DefaultRunnableSpec {

  class InMemoryPostRepository extends PostRepository {

    private val db = collection.mutable.Map[UUID, Post]()

    override def createPost(id: Post.Id, title: Post.Title, author: UserName, content: Post.Content): IO[Error, Post] = UIO {
      db(id.value) = Post(
        id = id,
        title = title,
        author = author,
        published = false,
        content = content
      )
      db(id.value)
    }

    override def findPostByTitle(title: Post.Title): IO[Error, Option[Post]] =
      UIO {
        db.values.find(_.title == title)
      }

    override def deletePost(id: Post.Id): IO[Error, Unit] = UIO {
      db.remove(id.value)
      ()
    }

    override def findPostById(id: Post.Id): IO[Error, Post] = UIO {
      db(id.value)
    }

    override def allPosts: IO[Error, List[Post]] = UIO {
      db.values.toList
    }
  }

  private val inMemoryRepo                        = new InMemoryPostRepository
  val testLayer: ULayer[Has[PostRepository.Pool]] =
    (() => ZManaged.succeed(inMemoryRepo)).toLayer

  override def spec: ZSpec[TestEnvironment, Any] = RepositorySpec.spec(repositoryType = "memory").provideCustomLayer(testLayer)

}
