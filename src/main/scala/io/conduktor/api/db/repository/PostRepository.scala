package io.conduktor.api.db.repository

import eu.timepit.refined.types.string.NonEmptyString
import io.conduktor.api.db.{DbSessionPool, createdAt, nonEmptyText}
import io.conduktor.api.types.UserName
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, Command, Fragment, Query}
import zio.interop.catz._
import zio.{Has, Task, ZIO, ZLayer}

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
}

object PostRepository {

  type PostRepository = Has[PostRepository.Service]

  trait Service {
    def createPost(id: UUID, title: NonEmptyString, author: UserName, content: String): Task[DbPost]

    def findPostByTitle(title: NonEmptyString): Task[Option[DbPost]]

    def deletePost(id: UUID): Task[Unit]

    def findPostById(id: UUID): Task[DbPost]

    //paginated
    def allPosts: ZIO[Any, Throwable, List[
      DbPost
    ]] // using fs2 stream (as tapir hasn't done the conversion for http4s yet https://github.com/softwaremill/tapir/issues/714 )

    //TODO example with LISTEN (ex: comments ?)
  }

  def createPost(id: UUID, title: NonEmptyString, author: UserName, content: String): ZIO[PostRepository, Throwable, DbPost] =
    ZIO.accessM(_.get.createPost(id, title, author, content))

  def deletePost(id: UUID): ZIO[PostRepository, Throwable, Unit] = ZIO.accessM(_.get.deletePost(id))

  def getPostById(id: UUID): ZIO[PostRepository, Throwable, DbPost] = ZIO.accessM(_.get.findPostById(id))

  def allPosts: ZIO[PostRepository, Throwable, List[DbPost]] = ZIO.accessM(_.get.allPosts)

  object Fragments {
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

  val live: ZLayer[Has[DbSessionPool.Service], Throwable, PostRepository] = ZLayer.fromServiceManaged { dbService: DbSessionPool.Service =>
    for {
      preAllocated <- dbService.pool.preallocateManaged
      pool         <- preAllocated
    } yield new Service {

      override def createPost(id: UUID, title: NonEmptyString, author: UserName, content: String): Task[DbPost] =
        pool.use {
          _.prepare(Fragments.postCreate).use(_.unique((id, title, author, content)))
        }

      override def deletePost(id: UUID): Task[Unit] =
        pool.use {
          _.prepare(Fragments.postDelete(Fragments.byId)).use(_.execute(id)).unit
        }

      override def findPostById(id: UUID): Task[DbPost] =
        pool.use {
          _.prepare(Fragments.postQuery(Fragments.byId)).use(_.unique(id))
        }

      // Skunk allows streaming pagination, but it requires keeping the connection opens
      override def allPosts: Task[List[DbPost]] =
        pool.use { session =>
          session.prepare(Fragments.postQuery(Fragment.empty)).use(_.stream(skunk.Void, 64).compile.toList)
        }

      override def findPostByTitle(title: NonEmptyString): Task[Option[DbPost]] =
        pool.use { session =>
          session.prepare(Fragments.postQuery(Fragments.byTitle)).use(_.option(title))
        }
    }
  }
}
