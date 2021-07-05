package io.conduktor.api.service

import io.conduktor.api.auth.User
import io.conduktor.api.model.Post
import io.conduktor.api.model.Post.{Content, Title}
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.service.PostService.{CreatePostError, DuplicatePostError, TechnicalPostError}
import zio._
import zio.random.Random

trait PostService {
  def createPost(user: User, title: Title, content: Content): ZIO[Random, CreatePostError, Post]

  def deletePost(id: Post.Id): Task[Unit]

  def findById(id: Post.Id): Task[Post]

  def all: Task[List[Post]]
}

object PostService {
  sealed trait PostServiceError

  sealed trait CreatePostError                              extends PostServiceError
  final case class DuplicatePostError(title: Title)         extends CreatePostError
  final case class TechnicalPostError(throwable: Throwable) extends CreatePostError
}

final class PostServiceLive(db: PostRepository) extends PostService {

  override def createPost(user: User, title: Title, content: Content): ZIO[Random, CreatePostError, Post] =
    for {
      random    <- ZIO.service[Random.Service]
      id        <- random.nextUUID
      maybePost <- db.findPostByTitle(title).mapError(TechnicalPostError)
      post      <- maybePost match {
                     case Some(_) => ZIO.fail(DuplicatePostError(title))
                     case None    => db.createPost(id, title, user.name, content).mapError(TechnicalPostError)
                   }
    } yield post

  override def deletePost(id: Post.Id): Task[Unit] = db.deletePost(id.value)

  override def findById(id: Post.Id): Task[Post] = db.findPostById(id.value)

  override def all: Task[List[Post]] = db.allPosts
}

object PostServiceLive {
  val layer: URLayer[Has[PostRepository], Has[PostService]] = (new PostServiceLive(_)).toLayer
}
