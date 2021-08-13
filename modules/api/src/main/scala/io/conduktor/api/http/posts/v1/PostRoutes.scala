package io.conduktor.api.http.posts.v1

import java.util.UUID

import io.conduktor.api.auth.AuthService
import io.conduktor.api.http.endpoints.secureEndpoint
import io.conduktor.api.http.{BadRequest, Conflict, ErrorInfo, ServerError}
import io.conduktor.api.model.{Post, User}
import io.conduktor.api.service.PostService
import io.conduktor.api.service.PostService.{CreatePostError, InvalidEmail}
import sttp.tapir.EndpointInput
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.ztapir._

import zio._
import zio.random.Random

object PostRoutes {
  import Domain._

  type Env = Has[AuthService] with Has[PostService] with Random

  private def serverError(defaultMessage: => String)(error: Throwable): ServerError =
    ServerError(Option(error.getMessage).getOrElse(defaultMessage))

  private def createPostServerLogic(user: User, post: CreatePostInput): ZIO[Has[PostService] with Random, ErrorInfo, PostDTO] =
    (for {
      service  <- ZIO.service[PostService]
      userName <- ZIO.fromEither(user.email.getUserName).orElseFail(InvalidEmail(user.email))
      created  <- service.createPost(userName, Post.Title(post.title), Post.Content(post.content))
    } yield created)
      .mapBoth(handleCreatePostError, PostDTO.from)

  private def deletePostServerLogic(id: UUID): ZIO[Has[PostService], ServerError, Unit] =
    ZIO
      .serviceWith[PostService](_.deletePost(Post.Id(id)))
      .mapError(serverError(s"Error deleting post $id"))

  private def getPostByIdServerLogic(id: UUID): ZIO[Has[PostService], ServerError, PostDTO] =
    ZIO
      .serviceWith[PostService](_.findById(Post.Id(id)))
      .mapBoth(serverError(s"Error finding post $id"), PostDTO.from)

  private def allPostsServerLogic: ZIO[Has[PostService], ServerError, List[PostDTO]] =
    ZIO
      .serviceWith[PostService](_.all)
      .mapBoth(serverError("Error listing posts"), _.map(PostDTO.from))

  private def handleCreatePostError(error: CreatePostError): ErrorInfo = {
    import cats.syntax.show._
    error match {
      case PostService.DuplicatePostError(title) => Conflict(show"A post already exists with the same title : $title.")
      case PostService.TechnicalPostError(err)   => serverError("Failed to create post.")(err)
      case InvalidEmail(email)                   => BadRequest(s"Invalid email ${email.value.value}")
    }
  }

  object Endpoints {

    val BASE_PATH: EndpointInput[Unit] = "posts" / "v1"

    private val createPostEndpoint  =
      secureEndpoint.post
        .in(BASE_PATH)
        .in(jsonBody[CreatePostInput])
        .out(jsonBody[PostDTO])
        .serverLogic { case (user, post) => createPostServerLogic(user, post) }

    private val deletePostEndpoint  =
      secureEndpoint.delete
        .in(BASE_PATH / path[UUID]("id"))
        .out(emptyOutput)
        .serverLogic { case (_, id) => deletePostServerLogic(id) }

    private val getPostByIdEndpoint =
      secureEndpoint.get
        .in(BASE_PATH / path[UUID]("id"))
        .out(jsonBody[PostDTO])
        .serverLogic { case (_, id) => getPostByIdServerLogic(id) }

    private val allPostsEndpoint    =
      secureEndpoint.get
        .in(BASE_PATH)
        .out(jsonBody[List[PostDTO]])
        .serverLogic(_ => allPostsServerLogic)

    val all: List[ZServerEndpoint[Env, _, _, _]] = List(
      createPostEndpoint.widen[Env],
      deletePostEndpoint.widen[Env],
      getPostByIdEndpoint.widen[Env],
      allPostsEndpoint.widen[Env]
    )

  }

}
