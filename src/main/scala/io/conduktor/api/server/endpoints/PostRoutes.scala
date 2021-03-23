package io.conduktor.api.server.endpoints

import java.util.UUID

import io.circe.generic.auto._
import io.conduktor.api.auth.UserAuthenticationLayer._
import io.conduktor.api.db.repository.PostRepository
import io.conduktor.api.db.repository.PostRepository.PostRepository
import io.conduktor.api.{db, server}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.ztapir._
import zio._

object PostRoutes {

  type Env = AuthService with PostRepository

  private implicit class RichDbPostMeta(self: db.PostMeta) {
    def toServer: server.PostMeta = server.PostMeta(
      id = self.id,
      createdAt = self.createdAt,
      published = self.published,
      author = self.author,
      title = self.title
    )
  }

  private implicit class RichDbPost(self: db.Post) {
    def toServer: server.Post = server.Post(
      meta = self.meta.toServer,
      content = self.content
    )
  }

  private def createPostServerLogic(user: User, post: server.CreatePostInput): ZIO[PostRepository, ErrorInfo, server.Post] =
    PostRepository.createPost(
      db.CreatePostInput(
        title = post.title,
        content = post.content,
        author = user.name
      )
    ).map(_.toServer)
      .mapError(err => ServerError(Option(err.getMessage).getOrElse("Error creating post")))

  private def deletePostServerLogic(id:  UUID) = PostRepository.deletePost(id)
    .mapError(err => ServerError(Option(err.getMessage).getOrElse("Error deleting post")))

  private def getPostByIdServerLogic(id:  UUID) = PostRepository.getPostById(id)
    .map(_.toServer)
    .mapError(err => ServerError(Option(err.getMessage).getOrElse("Error deleting post")))

//  private def allPosts = PostRepository.allPosts
//    .map(_.map(_.toServer))
//    .mapError(err => ServerError(Option(err.getMessage).getOrElse("Error deleting post")))


  object Endpoints {

    private val BASE_PATH = "posts"

    def all = List(
      createPostEndpoint,
      deletePostEndpoint,
      getPostByIdEndpoint
    )

    def createPostEndpoint = secureEndpoint
      .post
      .in(BASE_PATH)
      .in(jsonBody[server.CreatePostInput])
      .out(jsonBody[server.Post])
      .serverLogic {
        case (user, post) => createPostServerLogic(user, post)
      }

    def deletePostEndpoint = secureEndpoint
      .delete
      .in(BASE_PATH / path[UUID]("id"))
      .out(emptyOutput)
      .serverLogic {
        case (_, id) => deletePostServerLogic(id)
      }


    def getPostByIdEndpoint = secureEndpoint
      .get
      .in(BASE_PATH / path[UUID]("id"))
      .out(jsonBody[server.Post])
      .serverLogic {
        case (_, id) => getPostByIdServerLogic(id)
      }

//    val allPostsEndpoint = secureEndpoint
//      .get
//      .in("/post")
//      .out(streamBody(Fs2Streams[Task])(Schema(Schema.derived[server.Post].schemaType), CodecFormat.Json()))
//      .serverLogic {
//        case (_, id) => allPosts
//      }
  }

}
