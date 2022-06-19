package io.conduktor.api.http.posts.v1

import java.util.UUID

import io.conduktor.api.auth.AuthService
import io.conduktor.api.http.endpoints.secureEndpoint
import io.conduktor.api.http.{BadRequest, Conflict, ErrorInfo, NotFound, ServerError}
import io.conduktor.api.model.{Post, User}
import io.conduktor.api.repository.PostRepository.Error
import io.conduktor.api.service.PostService
import io.conduktor.api.service.PostService.{InvalidEmail, PostServiceError}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.EndpointInput
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
      .mapBoth(handlePostServiceError(s"Error creating post with title ${post.title}"), PostDTO.from)

  private def deletePostServerLogic(id: UUID): ZIO[Has[PostService], ErrorInfo, Unit] =
    ZIO
      .serviceWith[PostService](_.deletePost(Post.Id(id)))
      .mapError(handlePostServiceError(s"Error deleting post with id $id"))

  private def getPostByIdServerLogic(id: UUID): ZIO[Has[PostService], ErrorInfo, PostDTO] =
    ZIO
      .serviceWith[PostService](_.findById(Post.Id(id)))
      .mapBoth(handlePostServiceError(s"Error finding post $id"), PostDTO.from)

  private def allPostsServerLogic: ZIO[Has[PostService], ErrorInfo, List[PostDTO]] =
    ZIO
      .serviceWith[PostService](_.all)
      .mapBoth(handlePostServiceError("Error listing posts"), _.map(PostDTO.from))

  private def handlePostServiceError(context: String)(error: PostServiceError): ErrorInfo = {
    import cats.syntax.show._
    error match {
      case PostService.DuplicatePostError(title) =>
        Conflict(show"$context. A post already exists with the same title : ${title.value.value}.")
      case PostService.RepositoryError(err)      =>
        err match {
          case Error.PostNotFound(id) => NotFound(show"$context. No post found with id ${id.value}")
          case Error.Unexpected(_)    => ServerError(show"$context. Unexpected repository error. Check the logs for more")
        }
      case PostService.Unexpected(err)           => serverError(show"$context. Unexpected service error.")(err)
      case PostService.InvalidEmail(email)       => BadRequest(show"$context. Invalid email ${email.value.value}")
    }
  }

  object Endpoints {

    val BASE_PATH: EndpointInput[Unit] = "posts" / "v1"

    private val createPostEndpoint =
      secureEndpoint.post
        .in(BASE_PATH)
        .in(jsonBody[CreatePostInput])
        .out(jsonBody[PostDTO])
        .serverLogic(user => post => createPostServerLogic(user, post))

    private val deletePostEndpoint =
      secureEndpoint.delete
        .in(BASE_PATH / path[UUID]("id"))
        .out(emptyOutput)
        .serverLogic(_ => id => deletePostServerLogic(id))

    private val getPostByIdEndpoint =
      secureEndpoint.get
        .in(BASE_PATH / path[UUID]("id"))
        .out(jsonBody[PostDTO])
        .serverLogic(_ => id => getPostByIdServerLogic(id))

    private val allPostsEndpoint =
      secureEndpoint.get
        .in(BASE_PATH)
        .out(jsonBody[List[PostDTO]])
        .serverLogic(_ => _ => allPostsServerLogic)

    val all: List[ZServerEndpoint[Env, ZioStreams]] = List(
      createPostEndpoint.widen[Env],
      deletePostEndpoint.widen[Env],
      getPostByIdEndpoint.widen[Env],
      allPostsEndpoint.widen[Env]
    )

  }

}
