package io.conduktor.api.model

import eu.timepit.refined.types.string.NonEmptyString
import io.conduktor.api.auth.UserAuthenticationLayer.User
import io.estatico.newtype.macros.newtype

import java.util.UUID

final case class Post(
  id: UUID,
  title: NonEmptyString,
  author: User,
  published: Boolean,
  content: String
)
object Post {
  @newtype case class Title(value: NonEmptyString)
  @newtype case class Content(value: String)
}
