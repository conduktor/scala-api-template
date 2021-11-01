package io.conduktor.api.repository

import io.conduktor.api.db.DbSessionPool.SessionTask
import io.conduktor.api.model.Post
import io.conduktor.api.repository.PostRepository.Error
import io.conduktor.api.repository.db.DbPostRepository
import io.conduktor.primitives.types.UserName
import zio.{Has, IO, Managed, TaskManaged, ZLayer}

trait PostRepository {
  def createPost(id: Post.Id, title: Post.Title, author: UserName, content: Post.Content): IO[Error, Post]

  def findPostByTitle(title: Post.Title): IO[Error, Option[Post]]

  def deletePost(id: Post.Id): IO[Error, Unit]

  def findPostById(id: Post.Id): IO[Error, Post]

  // TODO paginated, zio stream
  def allPosts: IO[Error, List[
    Post
  ]]
}

object PostRepository extends zio.Accessible[PostRepository] {

  type Pool = Managed[Error.Unexpected, PostRepository]
  object Pool {
    def live: ZLayer[Has[TaskManaged[SessionTask]], Throwable, Has[Pool]] =
      (DbPostRepository.managed _).toLayer
  }

  sealed trait Error
  object Error {
    final case class PostNotFound(id: Post.Id)        extends Error
    final case class Unexpected(throwable: Throwable) extends Error
  }

}
