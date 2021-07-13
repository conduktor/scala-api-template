package io.conduktor.api.http.v1

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.conduktor.api.auth.{AuthService, User}
import io.conduktor.api.model.Post
import io.conduktor.api.service.PostService
import io.conduktor.api.service.PostService.CreatePostError
import io.conduktor.api.types.UserName
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.ztapir._
import zio._
import zio.random.Random

import java.util.UUID

final case class PostDTO(
  id: UUID,
  title: NonEmptyString,
  author: UserName,
  published: Boolean,
  content: String
)

object PostDTO {
  import io.circe.refined._
  implicit final val codec: Codec[PostDTO] = deriveCodec

  def from(p: Post): PostDTO =
    PostDTO(
      id = p.id.value,
      title = p.title.value,
      author = p.author.name,
      published = p.published,
      content = p.content.value
    )
}

final case class CreatePostInput(title: NonEmptyString, content: String)
object CreatePostInput {
  import io.circe.refined._
  implicit final val codec: Codec[CreatePostInput] = deriveCodec
}

object PostRoutes {

  type Env = Has[AuthService] with Has[PostService] with Random

  private def serverError(defaultMessage: => String)(error: Throwable): ServerError =
    ServerError(Option(error.getMessage).getOrElse(defaultMessage))

  private def createPostServerLogic(user: User, post: CreatePostInput): ZIO[Has[PostService] with Random, ErrorInfo, PostDTO] =
    (for {
      service <- ZIO.service[PostService]
      created <- service.createPost(user, Post.Title(post.title), Post.Content(post.content))
    } yield created)
      .bimap(handleCreatePostError, PostDTO.from)

  private def deletePostServerLogic(id: UUID): ZIO[Has[PostService], ServerError, Unit] =
    ZIO.serviceWith[PostService](_.deletePost(Post.Id(id)))
      .mapError(serverError(s"Error deleting post $id"))

  private def getPostByIdServerLogic(id: UUID): ZIO[Has[PostService], ServerError, PostDTO] =
    ZIO.serviceWith[PostService](_.findById(Post.Id(id)))
      .bimap(serverError(s"Error finding post $id"), PostDTO.from)

  private def allPostsServerLogic: ZIO[Has[PostService], ServerError, List[PostDTO]] =
    ZIO.serviceWith[PostService](_.all)
      .bimap(serverError("Error listing posts"), _.map(PostDTO.from))

  private def handleCreatePostError(error: CreatePostError): ErrorInfo = {
    import cats.syntax.show._
    error match {
      case PostService.DuplicatePostError(title) => Conflict(show"A post already exists with the same title : $title.")
      case PostService.TechnicalPostError(err) => serverError("FATAL ERROR")(err)
    }
  }

  object Endpoints {

    private val BASE_PATH = "posts"

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

    val all = List(
      createPostEndpoint.widen[Env],
      deletePostEndpoint.widen[Env],
      getPostByIdEndpoint.widen[Env],
      allPostsEndpoint.widen[Env]
    )

  }

}
