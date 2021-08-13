package io.conduktor.api.service

import eu.timepit.refined.auto._
import io.conduktor.api.db.MemoryRepositorySpec
import io.conduktor.api.model.Post
import io.conduktor.api.service.PostService.DuplicatePostError
import io.conduktor.primitives.types.UserName
import zio.ZIO
import zio.logging.Logging
import zio.magic._
import zio.test.Assertion.{equalTo, isLeft, isRight}
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object PostServiceSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Any] = suite("PostService")(
    testM("should fail to create two posts with the same title") {
      for {
        postService <- ZIO.service[PostService]
        r1          <- postService.createPost(author = UserName("ray"), title = Post.Title("title"), content = Post.Content("content1")).either
        r2          <- postService.createPost(author = UserName("ray"), title = Post.Title("title"), content = Post.Content("content2")).either

      } yield assert(r1)(isRight) && assert(r2)(isLeft(equalTo(DuplicatePostError(Post.Title("title")))))
    }
  ).injectCustom(MemoryRepositorySpec.testLayer, Logging.ignore, PostServiceLive.layer)
}
