package io.conduktor.api.service

import io.conduktor.api.model.Post
import io.conduktor.api.service.PostService.DuplicatePostError
import io.conduktor.primitives.types.UserName
import zio.ZIO
import zio.test.Assertion.{equalTo, isLeft, isRight}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}
import eu.timepit.refined.auto._
import io.conduktor.api.db.MemoryRepositorySpec

object PostServiceSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Any] = suite("PostService")(
    testM("should fail to create two posts with the same title") {
      for {
        postService <- ZIO.service[PostService]
        r1 <- postService.createPost(author = UserName("ray"), title = Post.Title("title"), content = Post.Content("content1")).either
        r2 <- postService.createPost(author = UserName("ray"), title = Post.Title("title"), content = Post.Content("content2")).either

      } yield assert(r1)(isRight) && assert(r2)(isLeft(equalTo(DuplicatePostError(Post.Title("title")))))
    }
  ).provideCustomLayer(MemoryRepositorySpec.testLayer >>> PostServiceLive.layer)
}
