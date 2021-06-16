package io.conduktor.api.db.repository

import eu.timepit.refined.types.string.NonEmptyString
import io.conduktor.api.auth.UserAuthenticationLayer.User
import io.conduktor.api.core.Post
import io.conduktor.api.db.DbSessionPool.SessionTask
import io.conduktor.api.db.{DbSessionPool, createdAt, nonEmptyText}
import io.conduktor.api.types.UserName
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, Command, Fragment, Query}
import zio.interop.catz._
import zio.{Has, Task, TaskManaged, URLayer, ZIO}

import java.time.LocalDateTime
import java.util.UUID

final case class DbPost(
  id: UUID,
  title: NonEmptyString,
  author: UserName,
  content: String,
  published: Boolean,
  createdAt: LocalDateTime
)
object DbPost {
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

  def createPost(id: UUID, title: Post.Title, author: UserName, content: Post.Content): ZIO[Has[PostRepository], Throwable, Post] =
    ZIO.accessM(_.get.createPost(id, title, author, content))

  def deletePost(id: UUID): ZIO[Has[PostRepository], Throwable, Unit] = ZIO.accessM(_.get.deletePost(id))

  def getPostById(id: UUID): ZIO[Has[PostRepository], Throwable, Post] = ZIO.accessM(_.get.findPostById(id))

  def allPosts: ZIO[Has[PostRepository], Throwable, List[Post]] = ZIO.accessM(_.get.allPosts)

  val layer: URLayer[Has[TaskManaged[DbSessionPool.SessionTask]], Has[PostRepository]] = (new DbPostRepository(_)).toLayer

}
