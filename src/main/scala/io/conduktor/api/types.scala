package io.conduktor.api

import eu.timepit.refined.types.all.NonEmptyString
import io.conduktor.api.repository.db
import io.estatico.newtype.macros.newtype

object types {

  @newtype case class UserName(value: NonEmptyString)
  object UserName {
    import io.circe.refined._
    import io.estatico.newtype.ops._
    import sttp.tapir.codec.refined._
    implicit final val decoder: io.circe.Decoder[UserName] = deriving
    implicit final val encoder: io.circe.Encoder[UserName] = deriving
    implicit final val schema: sttp.tapir.Schema[UserName] = deriving
    implicit final val codec: skunk.Codec[UserName]        = db.nonEmptyText.coerce
  }

}
