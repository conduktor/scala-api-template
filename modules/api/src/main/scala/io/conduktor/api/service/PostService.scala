package io.conduktor.api.service

import io.conduktor.api.auth.User
import io.conduktor.api.model.Post
import io.conduktor.api.model.Post.{Content, Title}
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.service.PostService.{CreatePostError, DuplicatePostError, TechnicalPostError}
import zio._

import java.util.UUID

trait PostService {
  def createPost(user: User, title: Title, content: Content): IO[CreatePostError, Post]

  def deletePost(uuid: UUID): Task[Unit]

  def findById(uuid: UUID): Task[Post]

  def all: Task[List[Post]]
}

object PostService {
  sealed trait PostServiceError

  sealed trait CreatePostError extends PostServiceError
  final case class DuplicatePostError(title: Title) extends CreatePostError
  final case class TechnicalPostError(throwable: Throwable) extends CreatePostError
}

final class PostServiceLive(db: PostRepository) extends PostService {

  override def createPost(user: User, title: Title, content: Content): IO[CreatePostError, Post] =
    for {
      maybePost <- db.findPostByTitle(title).mapError(TechnicalPostError)
      post      <- maybePost match {
                     case Some(_) => ZIO.fail(DuplicatePostError(title))
                     case None    => db.createPost(UUID.randomUUID(), title, user.name, content).mapError(TechnicalPostError)
                   }
    } yield post

  override def deletePost(id: UUID): Task[Unit] = db.deletePost(id)

  override def findById(id: UUID): Task[Post] = db.findPostById(id)

  override def all: Task[List[Post]] = db.allPosts
}

object PostServiceLive {
  val layer: URLayer[Has[PostRepository], Has[PostService]] = (new PostServiceLive(_)).toLayer
}
