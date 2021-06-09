package io.conduktor.api.server

import java.time.LocalDateTime
import java.util.UUID

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
  title: String,
  content: String
)
