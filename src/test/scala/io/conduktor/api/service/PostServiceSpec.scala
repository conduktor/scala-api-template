package io.conduktor.api.service

import eu.timepit.refined.auto._
import io.conduktor.api.model._
import zio.test.{DefaultRunnableSpec, ZSpec}
import zio._
import zio.test.Assertion._
import zio.test._
import io.conduktor.api._
import io.conduktor.api.types._
import io.conduktor.api.auth.User
import io.conduktor.api.service.PostService.DuplicatePostError
import zio.test.environment.TestEnvironment

object PostServiceSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Any] = suite("PostService") (
    testM("should fail to create two posts with the same title") {
      for {
        postService<- ZIO.service[PostService]
        r1 <- postService.createPost(User(name = UserName("user")), title = Post.Title("title"), content = Post.Content("content1")).either
        r2 <- postService.createPost(User(name = UserName("user2")), title = Post.Title("title"), content = Post.Content("content2")).either

      } yield assert(r1)(isRight) && assert(r2)(isLeft(equalTo(DuplicatePostError(Post.Title("title")))))
    }
  ).provideCustomLayer(MemoryRepositorySpec.testLayer >>> PostServiceLive.layer)
}
