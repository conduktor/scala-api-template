package io.conduktor.api.model

import cats.Show
import eu.timepit.refined.types.string.NonEmptyString
import io.conduktor.api.auth.User
import io.conduktor.api.model.Post.{Content, Id, Title}
import io.estatico.newtype.macros.newtype

import java.util.UUID

final case class Post(
  id: Id,
  title: Title,
  author: User,
  published: Boolean,
  content: Content
)
object Post {
  @newtype case class Id(value: UUID)

  @newtype case class Title(value: NonEmptyString)
  object Title {
    import eu.timepit.refined.cats._
    implicit final val show: Show[Title] = deriving
  }
  @newtype case class Content(value: String)
}
