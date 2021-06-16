package io.conduktor.api.http.v1

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.conduktor.api.auth.UserAuthenticationLayer._
import io.conduktor.api.core.PostService
import io.conduktor.api.core.types.Post
import io.conduktor.api.types.UserName
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.ztapir._
import zio._

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
      id = p.id,
      title = p.title,
      author = p.author.name,
      published = p.published,
      content = p.content
    )
}

final case class CreatePostInput(title: NonEmptyString, content: String)
object CreatePostInput {
  import io.circe.refined._
  implicit final val codec: Codec[CreatePostInput] = deriveCodec
}

object PostRoutes {

  type Env = AuthService with Has[PostService]

  private def serverError(defaultMessage: => String)(error: Throwable): ServerError =
    ServerError(Option(error.getMessage).getOrElse(defaultMessage))

  private def createPostServerLogic(user: User, post: CreatePostInput): ZIO[Has[PostService], ErrorInfo, PostDTO] =
    ZIO
      .accessM[Has[PostService]](_.get.createPost(user, Post.Title(post.title), Post.Content(post.content)))
      .bimap(serverError("Error creating post"), PostDTO.from)

  private def deletePostServerLogic(id: UUID): ZIO[Has[PostService], ServerError, Unit] =
    ZIO
      .accessM[Has[PostService]](_.get.deletePost(id))
      .mapError(serverError(s"Error deleting post $id"))

  private def getPostByIdServerLogic(id: UUID): ZIO[Has[PostService], ServerError, PostDTO] =
    ZIO
      .accessM[Has[PostService]](_.get.findById(id))
      .bimap(serverError(s"Error finding post $id"), PostDTO.from)

  private def allPostsServerLogic: ZIO[Has[PostService], ServerError, List[PostDTO]] =
    ZIO
      .accessM[Has[PostService]](_.get.all)
      .bimap(serverError("Error listing posts"), _.map(PostDTO.from))

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
      createPostEndpoint,
      deletePostEndpoint,
      getPostByIdEndpoint,
      allPostsEndpoint
    )

  }

}
