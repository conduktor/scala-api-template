package io.conduktor.api.db.repository

import java.util.UUID

import io.conduktor.api.db
import io.conduktor.api.db.DbSession
import skunk.codec.all._
import skunk.codec.text.varchar
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


    private val postMetaCodec : Codec[db.PostMeta] =  (uuid ~ varchar ~ varchar ~ bool ~ timestamp).gimap[db.PostMeta]
    private val postCodec : Codec[db.Post] =  (postMetaCodec ~ varchar).gimap[db.Post]


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
      sql"INSERT INTO post VALUES ($varchar, $varchar, $varchar) RETURNING $fullPostFields"
      .query(postCodec)
      .gcontramap[db.CreatePostInput]
    


  }


/*
I feel a bit weird using a session to prepare the queries
It means we have always a single session per service + keep session open forever ?
TODO test with a single-session pool and multiple services + verify session close/revive (set a connection timeout art a few secs and spam)
*/
 val live : ZLayer[Has[DbSession.Service], Throwable, PostRepository] = ZLayer.fromServiceManaged { dbService : DbSession.Service =>
   println("PostRepository live")

   for {
    session <-  dbService.session
    a = println("DbSession session inner")
    prepared <- (for {
      createPostPrepared <- session.prepare(Fragments.postCreate)
      a = println("prepared 1 inner")

      deletePostPrepared <- session.prepare(Fragments.postDelete(Fragments.byIdFragment))
      getPostByIdPrepared <- session.prepare(Fragments.postQuery(Fragments.byIdFragment))
      allPostsPrepared <- session.prepare(Fragments.postMetaQuery(Fragment.empty))
    } yield new Service {

      println("PostRepository live inner")


      override def createPost(input: db.CreatePostInput): ZIO[Any, Throwable, db.Post] = createPostPrepared.unique(input)

      override def deletePost(id: UUID): ZIO[Any, Throwable, Unit] = deletePostPrepared.execute(id).unit 
      
      override def getPostById(id: UUID): ZIO[Any, Throwable, db.Post] = getPostByIdPrepared.unique(id)
      
      override def allPosts: fs2.Stream[zio.Task,io.conduktor.api.db.PostMeta] = allPostsPrepared.stream(skunk.Void, 32)

       }).toManagedZIO  
  } yield prepared
 }
}

