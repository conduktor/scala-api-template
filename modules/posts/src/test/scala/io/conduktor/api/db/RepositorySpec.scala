package io.conduktor.api.db

import eu.timepit.refined.auto._
import io.conduktor.api.model.Post
import io.conduktor.api.repository.PostRepository
import io.conduktor.primitives.types.UserName
import zio.random.Random
import zio.test.Assertion.equalTo
import zio.test.environment.TestEnvironment
import zio.test.{ZSpec, assert, suite, testM}
import zio.{Has, ZIO}

object RepositorySpec {

  def spec(repositoryType: String): ZSpec[TestEnvironment with Has[PostRepository.Pool] with Random, Any] =
    suite(s"test the behavior of the repository $repositoryType")(
      testM(s"a created post can be retrieved by id") {
        // FIXME: inject database schema properly
        for {
          random <- ZIO.service[Random.Service]
          postId <- random.nextUUID.map(Post.Id.apply)
          pool   <- ZIO.service[PostRepository.Pool]
          actual <- pool.use(repo =>
                      repo.createPost(
                        id = postId,
                        title = Post.Title("hello"),
                        author = UserName("bob"),
                        content = Post.Content("testing")
                      ) *> repo.findPostById(postId)
                    )
        } yield assert(actual)(
          equalTo(
            Post(id = postId, title = Post.Title("hello"), author = UserName("bob"), published = false, content = Post.Content("testing"))
          )
        )
      }
    )
}
