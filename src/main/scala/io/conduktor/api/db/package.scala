package io.conduktor.api.db

import java.time.LocalDateTime
import java.util.UUID

import shapeless.tag
import shapeless.tag.@@

object Codecs {

  // TODO write a generic skunk codec for tagged types
  trait PostIdTag
  type PostId = UUID @@ PostIdTag
  object PostId {
    def apply(v: UUID): PostId = tag[PostIdTag][UUID](v)
  }
}

final case class Post(
  meta: PostMeta,
  content: String
)

final case class PostMeta(
  id: UUID,
  title: String,
  author: String,
  published: Boolean,
  createdAt: LocalDateTime
)

final case class CreatePostInput(
  id: UUID,
  title: String,
  author: String,
  content: String
)
