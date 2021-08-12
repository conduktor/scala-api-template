package io.conduktor.api.model

import eu.timepit.refined.types.all.NonEmptyString
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.conduktor.primitives.types.Email

final case class User(email: Email)
object User {
  import io.circe.refined._
  import io.estatico.newtype.ops._

  implicit val emailDecoder: Decoder[Email] = Decoder[NonEmptyString].coerce[Decoder[Email]]

  // Depending on the requirements, use a custom decoder here to extract data from the claims
  implicit val userDecoder: Decoder[User] = deriveDecoder
}
