package io.conduktor.api.model

import eu.timepit.refined.types.string.NonEmptyString
import io.conduktor.api.auth.User
import io.conduktor.api.model.Post.{Content, Title}
import io.estatico.newtype.macros.newtype

import java.util.UUID

final case class Post(
  id: UUID,
  title: Title,
  author: User,
  published: Boolean,
  content: Content
)
object Post {
  @newtype case class Title(value: NonEmptyString)
  @newtype case class Content(value: String)
}
