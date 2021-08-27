package io.conduktor.api.repository.db

import eu.timepit.refined.types.all.NonEmptyString
import io.conduktor.api.db.DbSessionPool.SessionTask
import io.conduktor.api.model.Post
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.repository.PostRepository.Error
import io.conduktor.api.repository.db.DbPostRepository.PreparedQueries
import io.conduktor.api.repository.db.SkunkExtensions._
import io.conduktor.primitives.types.UserName
import skunk.codec.all.{bool, uuid, text}
import skunk.{Codec, Fragment, PreparedQuery}
import zio.interop.catz._
import zio.{IO, Managed, Task, TaskManaged}

import java.time.LocalDateTime
import java.util.UUID

private[db] final case class PostDb(
  id: UUID,
  title: NonEmptyString,
  author: UserName,
  content: String,
  published: Boolean,
  createdAt: LocalDateTime
)
private[db] object PostDb {
  val codec: Codec[PostDb] =
    (uuid ~ nonEmptyText ~ usernameCodec ~ text ~ bool ~ createdAt).gimap[PostDb]

  def toDomain(p: PostDb): Post =
    Post(
      id = Post.Id(p.id),
      title = Post.Title(p.title),
      author = p.author,
      published = p.published,
      content = Post.Content(p.content)
    )
}

final class DbPostRepository(preparedQueries: PreparedQueries)(implicit
  private[db] val session: SessionTask
) extends PostRepository {

  override def createPost(id: Post.Id, title: Post.Title, author: UserName, content: Post.Content): IO[PostRepository.Error, Post] =
    Fragments.postCreate
      .unique((id.value, title.value, author, content.value))
      .map(PostDb.toDomain)

  override def deletePost(id: Post.Id): IO[PostRepository.Error, Unit] =
    Fragments.postDelete(Fragments.byId).execute(id.value).unit

  override def findPostById(id: Post.Id): IO[PostRepository.Error, Post] =
    preparedQueries.findById.unique(id.value).map(PostDb.toDomain).wrapException

  // Skunk allows streaming pagination, but it requires keeping the connection opens
  override def allPosts: IO[PostRepository.Error, List[Post]] =
    Fragments.postQuery(Fragment.empty).list(skunk.Void, 64).map(_.map(PostDb.toDomain))

  override def findPostByTitle(title: Post.Title): IO[PostRepository.Error, Option[Post]] =
    Fragments.postQuery(Fragments.byTitle).option(title.value).map(_.map(PostDb.toDomain))
}

object DbPostRepository {

  private case class PreparedQueries(
    findById: PreparedQuery[Task, UUID, PostDb]
  )

  /**
   * Used to create a "pool of repository" mapped from a pool of DB session That ensure that one and only one session is used per user
   * request, preventing eventual "portal_xx not found" issues and simplifying the repository implementation
   *
   * Here we demo preparing a statement in advance, only do it here if it's going to be used by most of your requests
   */
  def managed(sessionPool: TaskManaged[SessionTask]): Managed[Error.Unexpected, DbPostRepository] = for {
    // we retrieve a session from the pool
    session  <- sessionPool.wrapException
    findById <- session.prepare(Fragments.postQuery(Fragments.byId)).toManagedZIO.wrapException

  } yield new DbPostRepository(PreparedQueries(findById = findById))(session)

}
