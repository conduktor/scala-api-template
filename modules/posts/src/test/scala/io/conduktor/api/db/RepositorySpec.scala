package io.conduktor.api.db

import io.conduktor.api.model.Post
import io.conduktor.api.repository.PostRepository
import zio.test.Assertion.equalTo
import zio.test.environment.TestEnvironment
import zio.test.{ZSpec, assert, suite, testM}
import zio.{Has, ZIO}
import eu.timepit.refined.auto._
import io.conduktor.primitives.types.UserName

import java.util.UUID

object RepositorySpec {

  def spec(repositoryType: String): ZSpec[TestEnvironment with Has[PostRepository], Any] =
    suite(s"test the behavior of the repository $repositoryType")(
      testM(s"a created post can be retrieved by id") {
        //FIXME: inject database schema properly
        //FIXME: extract uuid into a zlayer
        val id = UUID.fromString("08d7d61d-7e69-44b7-b66e-4203c192ff82")
        for {
          repo <- ZIO.service[PostRepository]
          _ <- repo.createPost(
            id = id,
            title = Post.Title("hello"),
            author = UserName("bob"),
            content = Post.Content("testing")
          )
          actual <- repo.findPostById(id)
        } yield assert(actual)(
          equalTo(
            Post(id = Post.Id(id), title = Post.Title("hello"), author = UserName("bob"), published = false, content = Post.Content("testing"))
          )
        )
      }
    )
}
