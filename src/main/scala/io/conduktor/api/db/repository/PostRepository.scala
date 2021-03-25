package io.conduktor.api.db.repository



import java.util.UUID

import io.conduktor.api.db
import io.conduktor.api.db.{DbSession, DbSessionPool}
import skunk.codec.all._
import skunk.implicits._
import skunk.{Codec, Command, Fragment, Query}
import zio.interop.catz._
import zio.{Has, ZIO, ZLayer}


object PostRepository {

  type PostRepository = Has[PostRepository.Service]
  trait Service {
    def createPost(input:db.CreatePostInput): ZIO[Any, Throwable, db.Post]
    def deletePost(id:UUID): ZIO[Any, Throwable, Unit]
    def getPostById(id:UUID): ZIO[Any, Throwable, db.Post]
    //paginated
    def allPosts: fs2.Stream[zio.Task,db.PostMeta] // using fs2 stream (as tapir hasn't done the conversion for http4s yet https://github.com/softwaremill/tapir/issues/714 )

    //TODO example with LISTEN (ex: comments ?)
  }
  def createPost(input:db.CreatePostInput): ZIO[PostRepository, Throwable, db.Post] = ZIO.accessM(_.get.createPost(input))
  def deletePost(id:UUID): ZIO[PostRepository, Throwable, Unit] = ZIO.accessM(_.get.deletePost(id))
  def getPostById(id:UUID): ZIO[PostRepository, Throwable, db.Post] = ZIO.accessM(_.get.getPostById(id))
  def allPosts: ZIO[PostRepository, Nothing, fs2.Stream[zio.Task,db.PostMeta]] = ZIO.access(_.get.allPosts)

  object Fragments {


    private val postMetaCodec : Codec[db.PostMeta] =  (uuid ~ text ~ text ~ bool ~ timestamp(3)).gimap[db.PostMeta]
    private val postCodec : Codec[db.Post] =  (postMetaCodec ~ text).gimap[db.Post]


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


 val live : ZLayer[Has[DbSessionPool.Service], Throwable, PostRepository] = ZLayer.fromServiceManaged { dbService : DbSessionPool.Service =>

   for {
     pool <-  dbService.pool
     session <-  pool
    prepared <- (for {
      createPostPrepared <- session.prepare(Fragments.postCreate)
      deletePostPrepared <- session.prepare(Fragments.postDelete(Fragments.byIdFragment))
      getPostByIdPrepared <- session.prepare(Fragments.postQuery(Fragments.byIdFragment))
      allPostsPrepared <- session.prepare(Fragments.postMetaQuery(Fragment.empty))
    } yield new Service {

      override def createPost(input: db.CreatePostInput): ZIO[Any, Throwable, db.Post] = pool.use {
        session => session.unique(Fragments.postCreate.contramap(_ => input))
      }

      override def deletePost(id: UUID): ZIO[Any, Throwable, Unit] = deletePostPrepared.execute(id).unit

      override def getPostById(id: UUID): ZIO[Any, Throwable, db.Post] = getPostByIdPrepared.unique(id)
      
      override def allPosts: fs2.Stream[zio.Task,io.conduktor.api.db.PostMeta] = allPostsPrepared.stream(skunk.Void, 32)

       }).toManagedZIO  
  } yield prepared
 }
}

