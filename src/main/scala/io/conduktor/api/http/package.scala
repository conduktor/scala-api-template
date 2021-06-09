package io.conduktor.api.http

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.refined._
import io.conduktor.api.db.repository

import java.time.LocalDateTime
import java.util.UUID

final case class Post(meta: PostMeta, content: NonEmptyString)
object Post {
  implicit final val codec: Codec[Post] = deriveCodec

  def from(it: repository.Post): Post =
    Post(
      meta = PostMeta.from(it.meta),
      content = it.content
    )
}

final case class PostMeta(
  id: UUID,
  title: NonEmptyString,
  author: NonEmptyString,
  published: Boolean,
  createdAt: LocalDateTime
)
object PostMeta {
  implicit final val codec: Codec[PostMeta] = deriveCodec

  def from(it: repository.PostMeta): PostMeta =
    PostMeta(
      id = it.id,
      createdAt = it.createdAt,
      published = it.published,
      author = it.author,
      title = it.title
    )
}

final case class CreatePostInput(title: NonEmptyString, content: NonEmptyString)
object CreatePostInput {
  implicit final val codec: Codec[CreatePostInput] = deriveCodec
}
