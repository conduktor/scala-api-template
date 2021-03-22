package io.conduktor.api.server


import java.time.LocalDateTime
import java.util.UUID

case class Post(
  meta: PostMeta,
  content: String,
)

case class PostMeta(
  id: UUID,
  title: String,
  author: String,
  published: Boolean,
  createdAt: LocalDateTime
)

 case class CreatePostInput(
  title: String,
  content: String
)
