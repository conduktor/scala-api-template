package io.conduktor.api.repository

import io.conduktor.api.model.Post
import io.conduktor.api.types.UserName
import zio.{Has, Task, ZIO}

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

object PostRepository {
  def createPost(id: UUID, title: Post.Title, author: UserName, content: Post.Content): ZIO[Has[PostRepository], Throwable, Post] =
    ZIO.serviceWith(_.createPost(id, title, author, content))

  def deletePost(id: UUID): ZIO[Has[PostRepository], Throwable, Unit] = ZIO.serviceWith(_.deletePost(id))

  def getPostById(id: UUID): ZIO[Has[PostRepository], Throwable, Post] = ZIO.serviceWith(_.findPostById(id))

  def allPosts: ZIO[Has[PostRepository], Throwable, List[Post]] = ZIO.serviceWith(_.allPosts)
}