package io.conduktor.api.db.repository


import java.util.UUID

import io.conduktor.api.db
import io.conduktor.api.db.DbSessionPool
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, Command, Fragment, Query}
import zio.interop.catz._
import zio.{Has, ZIO, ZLayer}


object PostRepository {

  type PostRepository = Has[PostRepository.Service]

  trait Service {
    def createPost(input: db.CreatePostInput): ZIO[Any, Throwable, db.Post]

    def deletePost(id: UUID): ZIO[Any, Throwable, Unit]

    def getPostById(id: UUID): ZIO[Any, Throwable, db.Post]

    //paginated
    def allPosts: ZIO[Any, Throwable, List[db.PostMeta]] // using fs2 stream (as tapir hasn't done the conversion for http4s yet https://github.com/softwaremill/tapir/issues/714 )

    //TODO example with LISTEN (ex: comments ?)
  }

  def createPost(input: db.CreatePostInput): ZIO[PostRepository, Throwable, db.Post] = ZIO.accessM(_.get.createPost(input))

  def deletePost(id: UUID): ZIO[PostRepository, Throwable, Unit] = ZIO.accessM(_.get.deletePost(id))

  def getPostById(id: UUID): ZIO[PostRepository, Throwable, db.Post] = ZIO.accessM(_.get.getPostById(id))

  def allPosts: ZIO[PostRepository, Throwable, List[db.PostMeta]] = ZIO.accessM(_.get.allPosts)

  object Fragments {


    private val postMetaCodec: Codec[db.PostMeta] = (uuid ~ text ~ text ~ bool ~ timestamp(3)).gimap[db.PostMeta]
    private val postCodec: Codec[db.Post] = (postMetaCodec ~ text).gimap[db.Post]


    val fullPostFields: Fragment[skunk.Void] = sql"id, title, author, published, created_at, content"
    val byIdFragment: Fragment[UUID] = sql"where id = $uuid"


    def postMetaQuery[A](where: Fragment[A]): Query[A, db.PostMeta] =
      sql"SELECT id, title, author, published, created_at FROM post $where".query(postMetaCodec)

    def postQuery[A](where: Fragment[A]): Query[A, db.Post] =
      sql"SELECT $fullPostFields FROM post $where".query(postCodec)

    def postDelete[A](where: Fragment[A]): Command[A] =
      sql"DELETE FROM post $where".command

    // using a Query to retrieve user
    def postCreate: Query[db.CreatePostInput, db.Post] =
      sql"INSERT INTO post (id, title, author, content) VALUES ($uuid, $text, $text, $text) RETURNING $fullPostFields"
        .query(postCodec)
        .gcontramap[db.CreatePostInput]
  }

  val live: ZLayer[Has[DbSessionPool.Service], Throwable, PostRepository] = ZLayer.fromServiceManaged { dbService: DbSessionPool.Service =>

    for {
      preAllocated <- dbService.pool.preallocateManaged
      pool <- preAllocated
    } yield new Service {

      override def createPost(input: db.CreatePostInput): ZIO[Any, Throwable, db.Post] = {
        pool.use {
          _.prepare(Fragments.postCreate).use(_.unique(input))
        }
      }

      override def deletePost(id: UUID): ZIO[Any, Throwable, Unit] =
        pool.use {
          _.prepare(Fragments.postDelete(Fragments.byIdFragment)).use(_.execute(id)).unit
        }

      override def getPostById(id: UUID): ZIO[Any, Throwable, db.Post] =
        pool.use {
          _.prepare(Fragments.postQuery(Fragments.byIdFragment)).use(_.unique(id))
        }

      // Skunk allows streaming pagination, but it requires keeping the connection opens
      override def allPosts: ZIO[Any, Throwable, List[db.PostMeta]] =
        pool.use { session =>
          session.prepare(Fragments.postMetaQuery(Fragment.empty)).use(_.stream(skunk.Void, 64).compile.toList)
        }


//      override def allPostsPaginated(offset:Int, num:Int): ZIO[Any, Throwable, List[db.PostMeta]] =
//        pool.use { session =>
//          session.prepare(Fragments.postMetaQuery(Fragment.postMetaOffset)).use(_.stream(Offset, 32).compile.toList)
//        }
    }
  }
}

