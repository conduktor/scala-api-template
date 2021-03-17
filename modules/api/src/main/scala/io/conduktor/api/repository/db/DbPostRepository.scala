package io.conduktor.api.repository.db

import eu.timepit.refined.types.string.NonEmptyString
import io.conduktor.api.auth.User
import io.conduktor.api.model.Post
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.repository.db.DbSessionPool.SessionTask
import io.conduktor.api.types.UserName
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, Command, Fragment, Query}
import zio.interop.catz._
import zio.{Has, Task, TaskManaged, URLayer}

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
private object PostDb {
  private[db] val codec: Codec[PostDb] =
    (uuid ~ nonEmptyText ~ UserName.codec ~ text ~ bool ~ createdAt).gimap[PostDb]

  def toDomain(p: PostDb): Post =
    Post(
      id = Post.Id(p.id),
      title = Post.Title(p.title),
      author = User(name = p.author),
      published = p.published,
      content = Post.Content(p.content)
    )
}

final class DbPostRepository(session: TaskManaged[SessionTask]) extends PostRepository {

  private object Fragments {
    val fullPostFields: Fragment[skunk.Void] = sql"id, title, author, content, published, created_at"
    val byId: Fragment[UUID]                 = sql"where id = $uuid"
    val byTitle: Fragment[NonEmptyString]    = sql"where title = $nonEmptyText"

    def postQuery[A](where: Fragment[A]): Query[A, PostDb] =
      sql"SELECT $fullPostFields FROM post $where".query(PostDb.codec)

    def postDelete[A](where: Fragment[A]): Command[A] =
      sql"DELETE FROM post $where".command

    // using a Query to retrieve user
    def postCreate: Query[(UUID, NonEmptyString, UserName, String), PostDb] =
      sql"""
        INSERT INTO post (id, title, author, content)
        VALUES ($uuid, $nonEmptyText, ${UserName.codec}, $text)
        RETURNING $fullPostFields
      """
        .query(PostDb.codec)
        .gcontramap[(UUID, NonEmptyString, UserName, String)]
  }

  override def createPost(id: UUID, title: Post.Title, author: UserName, content: Post.Content): Task[Post] =
    session.use {
      _.prepare(Fragments.postCreate).use(_.unique((id, title.value, author, content.value)))
    }.map(PostDb.toDomain)

  override def deletePost(id: UUID): Task[Unit] =
    session.use {
      _.prepare(Fragments.postDelete(Fragments.byId)).use(_.execute(id)).unit
    }

  override def findPostById(id: UUID): Task[Post] =
    session.use {
      _.prepare(Fragments.postQuery(Fragments.byId)).use(_.unique(id))
    }.map(PostDb.toDomain)

  // Skunk allows streaming pagination, but it requires keeping the connection opens
  override def allPosts: Task[List[Post]] =
    session.use { session =>
      session.prepare(Fragments.postQuery(Fragment.empty)).use(_.stream(skunk.Void, 64).compile.toList)
    }.map(_.map(PostDb.toDomain))

  override def findPostByTitle(title: Post.Title): Task[Option[Post]] =
    session.use { session =>
      session.prepare(Fragments.postQuery(Fragments.byTitle)).use(_.option(title.value))
    }.map(_.map(PostDb.toDomain))
}

object DbPostRepository {

  val layer: URLayer[Has[TaskManaged[DbSessionPool.SessionTask]], Has[PostRepository]] = (new DbPostRepository(_)).toLayer

}
