package io.conduktor.api.http.v1

import io.conduktor.api.auth.UserAuthenticationLayer._
import io.conduktor.api.db.repository.PostRepository
import io.conduktor.api.db.repository.PostRepository.PostRepository
import io.conduktor.api.http
import io.conduktor.api.http.{Post, PostMeta}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.ztapir._
import zio._

import java.util.UUID

object PostRoutes {

  type Env = AuthService with PostRepository

  private def createPostServerLogic(user: User, post: http.CreatePostInput): ZIO[PostRepository, ErrorInfo, http.Post] =
    (for {
      uuid <- ZIO.effect(java.util.UUID.randomUUID())
      post <- PostRepository
                .createPost(
                  id = uuid,
                  title = post.title,
                  content = post.content,
                  author = user.name
                )
                .map(Post.from)
    } yield post)
      .mapError(err => ServerError(Option(err.getMessage).getOrElse("Error creating post")))

  private def deletePostServerLogic(id: UUID) =
    PostRepository
      .deletePost(id)
      .mapError(err => ServerError(Option(err.getMessage).getOrElse("Error deleting post")))

  private def getPostByIdServerLogic(id: UUID) =
    PostRepository
      .getPostById(id)
      .bimap(err => ServerError(Option(err.getMessage).getOrElse("Error deleting post")), Post.from)

  private def allPostsServerLogic =
    PostRepository.allPosts
      .bimap(err => ServerError(Option(err.getMessage).getOrElse("Error listing post")), _.map(PostMeta.from))

  object Endpoints {

    private val BASE_PATH = "posts"

    def all = List(
      createPostEndpoint,
      deletePostEndpoint,
      getPostByIdEndpoint,
      allPostsEndpoint
    )

    def createPostEndpoint =
      secureEndpoint.post
        .in(BASE_PATH)
        .in(jsonBody[http.CreatePostInput])
        .out(jsonBody[http.Post])
        .serverLogic { case (user, post) =>
          createPostServerLogic(user, post)
        }

    def deletePostEndpoint =
      secureEndpoint.delete
        .in(BASE_PATH / path[UUID]("id"))
        .out(emptyOutput)
        .serverLogic { case (_, id) =>
          deletePostServerLogic(id)
        }

    def getPostByIdEndpoint =
      secureEndpoint.get
        .in(BASE_PATH / path[UUID]("id"))
        .out(jsonBody[http.Post])
        .serverLogic { case (_, id) =>
          getPostByIdServerLogic(id)
        }

    def allPostsEndpoint =
      secureEndpoint.get
        .in(BASE_PATH)
        .out(jsonBody[List[http.PostMeta]])
        .serverLogic(_ => allPostsServerLogic)

  }

}
