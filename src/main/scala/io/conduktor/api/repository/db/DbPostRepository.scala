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

private final case class DbPost(
  id: UUID,
  title: NonEmptyString,
  author: UserName,
  content: String,
  published: Boolean,
  createdAt: LocalDateTime
)
private object DbPost {
  private[repository] val codec: Codec[DbPost] =
    (uuid ~ nonEmptyText ~ UserName.codec ~ text ~ bool ~ createdAt).gimap[DbPost]

  def toDomain(p: DbPost): Post =
    Post(
      id = p.id,
      title = p.title,
      author = User(name = p.author),
      published = p.published,
      content = p.content
    )
}

final class DbPostRepository(session: TaskManaged[SessionTask]) extends PostRepository {

  private object Fragments {
    val fullPostFields: Fragment[skunk.Void] = sql"id, title, author, content, published, created_at"
    val byId: Fragment[UUID]                 = sql"where id = $uuid"
    val byTitle: Fragment[NonEmptyString]    = sql"where title = $nonEmptyText"

    def postQuery[A](where: Fragment[A]): Query[A, DbPost] =
      sql"SELECT $fullPostFields FROM post $where".query(DbPost.codec)

    def postDelete[A](where: Fragment[A]): Command[A] =
      sql"DELETE FROM post $where".command

    // using a Query to retrieve user
    def postCreate: Query[(UUID, NonEmptyString, UserName, String), DbPost] =
      sql"""
        INSERT INTO post (id, title, author, content)
        VALUES ($uuid, $nonEmptyText, ${UserName.codec}, $text)
        RETURNING $fullPostFields
      """
        .query(DbPost.codec)
        .gcontramap[(UUID, NonEmptyString, UserName, String)]
  }

  override def createPost(id: UUID, title: Post.Title, author: UserName, content: Post.Content): Task[Post] =
    session.use {
      _.prepare(Fragments.postCreate).use(_.unique((id, title.value, author, content.value)))
    }.map(DbPost.toDomain)

  override def deletePost(id: UUID): Task[Unit] =
    session.use {
      _.prepare(Fragments.postDelete(Fragments.byId)).use(_.execute(id)).unit
    }

  override def findPostById(id: UUID): Task[Post] =
    session.use {
      _.prepare(Fragments.postQuery(Fragments.byId)).use(_.unique(id))
    }.map(DbPost.toDomain)

  // Skunk allows streaming pagination, but it requires keeping the connection opens
  override def allPosts: Task[List[Post]] =
    session.use { session =>
      session.prepare(Fragments.postQuery(Fragment.empty)).use(_.stream(skunk.Void, 64).compile.toList)
    }.map(_.map(DbPost.toDomain))

  override def findPostByTitle(title: Post.Title): Task[Option[Post]] =
    session.use { session =>
      session.prepare(Fragments.postQuery(Fragments.byTitle)).use(_.option(title.value))
    }.map(_.map(DbPost.toDomain))
}

object DbPostRepository {

  val layer: URLayer[Has[TaskManaged[DbSessionPool.SessionTask]], Has[PostRepository]] = (new DbPostRepository(_)).toLayer

}