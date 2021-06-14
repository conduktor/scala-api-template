package io.conduktor.api.core

import eu.timepit.refined.types.string.NonEmptyString
import io.conduktor.api.auth.UserAuthenticationLayer.User
import io.conduktor.api.db.repository.PostRepository.PostRepository
import io.conduktor.api.db.repository.{DbPost, PostRepository}
import zio.{Has, Task, ZIO, ZLayer}

import java.util.UUID

final case class Post(
  id: UUID,
  title: NonEmptyString,
  author: User,
  published: Boolean,
  content: String
)
object Post {
  def from(p: DbPost): Post =
    Post(
      id = p.id,
      title = p.title,
      author = User(name = p.author),
      published = p.published,
      content = p.content
    )
}

trait PostService  {
  def createPost(user: User, title: NonEmptyString, content: String): Task[Post]

  def deletePost(uuid: UUID): Task[Unit]

  def findById(uuid: UUID): Task[Post]

  def all: Task[List[Post]]
}
object PostService {
  type PostService = Has[PostServiceLive]

  val live: ZLayer[PostRepository, Nothing, PostService] = ZLayer.fromService(new PostServiceLive(_))
}

final class PostServiceLive(db: PostRepository.Service) extends PostService {

  override def createPost(user: User, title: NonEmptyString, content: String): Task[Post] =
    for {
      maybePost <- db.findPostByTitle(title)
      post      <- maybePost match {
                     case Some(post) => ZIO.succeed(post)
                     case None       => db.createPost(UUID.randomUUID(), title, user.name, content)
                   }
    } yield Post.from(post)

  override def deletePost(id: UUID): Task[Unit] = db.deletePost(id)

  override def findById(id: UUID): Task[Post] = db.findPostById(id).map(Post.from)

  override def all: Task[List[Post]] = db.allPosts.map(_.map(Post.from))
}
