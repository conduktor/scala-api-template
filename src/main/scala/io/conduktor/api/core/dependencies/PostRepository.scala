package io.conduktor.api.core.dependencies

import io.conduktor.api.core.types.Post
import io.conduktor.api.types.UserName
import zio.{Task, ZIO}

import java.util.UUID

trait PostRepository {
  def createPost(id: UUID, title: Post.Title, author: UserName, content: Post.Content): Task[Post]

  def findPostByTitle(title: Post.Title): Task[Option[Post]]

  def deletePost(id: UUID): Task[Unit]

  def findPostById(id: UUID): Task[Post]

  //paginated
  def allPosts: ZIO[Any, Throwable, List[
    Post
  ]] // using fs2 stream (as tapir hasn't done the conversion for http4s yet https://github.com/softwaremill/tapir/issues/714 )

  //TODO example with LISTEN (ex: comments ?)
}
