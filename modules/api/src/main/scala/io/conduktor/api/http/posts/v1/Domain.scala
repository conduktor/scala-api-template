package io.conduktor.api.http.posts.v1

import java.util.UUID

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}
import io.conduktor.api.model.Post
import io.conduktor.primitives.types.UserName
import sttp.tapir.Schema
import sttp.tapir.codec.newtype.TapirCodecNewType
import sttp.tapir.codec.refined.TapirCodecRefined

private[v1] object Domain extends TapirCodecRefined with TapirCodecNewType {

  import io.circe.refined._
  import io.estatico.newtype.ops._

  implicit val usernameEncoder: Encoder[UserName] = Encoder[NonEmptyString].coerce[Encoder[UserName]]
  implicit val usernameDecoder: Decoder[UserName] = Decoder[NonEmptyString].coerce[Decoder[UserName]]

  final case class PostDTO(
    id: UUID,
    title: NonEmptyString,
    author: UserName,
    published: Boolean,
    content: String
  )
  object PostDTO {
    implicit final val encoder: Codec[PostDTO] = deriveCodec
    implicit val schema: Schema[PostDTO]       = Schema.derived

    def from(p: Post): PostDTO =
      PostDTO(
        id = p.id.value,
        title = p.title.value,
        author = p.author,
        published = p.published,
        content = p.content.value
      )
  }

  final case class CreatePostInput(title: NonEmptyString, content: String)
  object CreatePostInput {
    implicit final val codec: Codec[CreatePostInput] = deriveCodec
    implicit val schema: Schema[CreatePostInput]     = Schema.derived

  }
}
