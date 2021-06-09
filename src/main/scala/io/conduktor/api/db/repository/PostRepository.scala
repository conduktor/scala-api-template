package io.conduktor.api.db.repository

import eu.timepit.refined.types.string.NonEmptyString
import io.conduktor.api.db.{DbSessionPool, nonEmptyText}
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, Command, Fragment, Query}
import zio.interop.catz._
import zio.{Has, ZIO, ZLayer}

import java.time.LocalDateTime
import java.util.UUID

final case class PostMeta(
  id: UUID,
  title: NonEmptyString,
  author: NonEmptyString,
  published: Boolean,
  createdAt: LocalDateTime
)
final case class Post(meta: PostMeta, content: NonEmptyString)

object PostRepository {

  type PostRepository = Has[PostRepository.Service]

  trait Service {
    def createPost(id: UUID, title: NonEmptyString, author: NonEmptyString, content: NonEmptyString): ZIO[Any, Throwable, Post]

    def deletePost(id: UUID): ZIO[Any, Throwable, Unit]

    def getPostById(id: UUID): ZIO[Any, Throwable, Post]

    //paginated
    def allPosts: ZIO[Any, Throwable, List[
      PostMeta
    ]] // using fs2 stream (as tapir hasn't done the conversion for http4s yet https://github.com/softwaremill/tapir/issues/714 )

    //TODO example with LISTEN (ex: comments ?)
  }

  def createPost(id: UUID, title: NonEmptyString, author: NonEmptyString, content: NonEmptyString): ZIO[PostRepository, Throwable, Post] =
    ZIO.accessM(_.get.createPost(id, title, author, content))

  def deletePost(id: UUID): ZIO[PostRepository, Throwable, Unit] = ZIO.accessM(_.get.deletePost(id))

  def getPostById(id: UUID): ZIO[PostRepository, Throwable, Post] = ZIO.accessM(_.get.getPostById(id))

  def allPosts: ZIO[PostRepository, Throwable, List[PostMeta]] = ZIO.accessM(_.get.allPosts)

  object Fragments {

    private val postMetaCodec: Codec[PostMeta] = (uuid ~ nonEmptyText ~ nonEmptyText ~ bool ~ timestamp(3)).gimap[PostMeta]
    private val postCodec: Codec[Post]         = (postMetaCodec ~ nonEmptyText).gimap[Post]

    val fullPostFields: Fragment[skunk.Void] = sql"id, title, author, published, created_at, content"
    val byIdFragment: Fragment[UUID]         = sql"where id = $uuid"

    def postMetaQuery[A](where: Fragment[A]): Query[A, PostMeta] =
      sql"SELECT id, title, author, published, created_at FROM post $where".query(postMetaCodec)

    def postQuery[A](where: Fragment[A]): Query[A, Post] =
      sql"SELECT $fullPostFields FROM post $where".query(postCodec)

    def postDelete[A](where: Fragment[A]): Command[A] =
      sql"DELETE FROM post $where".command

    // using a Query to retrieve user
    def postCreate: Query[(UUID, NonEmptyString, NonEmptyString, NonEmptyString), Post] =
      sql"""
        INSERT INTO post (id, title, author, content)
        VALUES ($uuid, $nonEmptyText, $nonEmptyText, $nonEmptyText)
        RETURNING $fullPostFields
      """
        .query(postCodec)
        .gcontramap[(UUID, NonEmptyString, NonEmptyString, NonEmptyString)]
  }

  val live: ZLayer[Has[DbSessionPool.Service], Throwable, PostRepository] = ZLayer.fromServiceManaged { dbService: DbSessionPool.Service =>
    for {
      preAllocated <- dbService.pool.preallocateManaged
      pool         <- preAllocated
    } yield new Service {

      override def createPost(id: UUID, title: NonEmptyString, author: NonEmptyString, content: NonEmptyString): ZIO[Any, Throwable, Post] =
        pool.use {
          _.prepare(Fragments.postCreate).use(_.unique((id, title, author, content)))
        }

      override def deletePost(id: UUID): ZIO[Any, Throwable, Unit] =
        pool.use {
          _.prepare(Fragments.postDelete(Fragments.byIdFragment)).use(_.execute(id)).unit
        }

      override def getPostById(id: UUID): ZIO[Any, Throwable, Post] =
        pool.use {
          _.prepare(Fragments.postQuery(Fragments.byIdFragment)).use(_.unique(id))
        }

      // Skunk allows streaming pagination, but it requires keeping the connection opens
      override def allPosts: ZIO[Any, Throwable, List[PostMeta]] =
        pool.use { session =>
          session.prepare(Fragments.postMetaQuery(Fragment.empty)).use(_.stream(skunk.Void, 64).compile.toList)
        }

    }
  }
}
