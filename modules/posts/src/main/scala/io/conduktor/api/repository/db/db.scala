package io.conduktor.api.repository

import eu.timepit.refined.types.all.NonEmptyString
import io.conduktor.primitives.types.UserName
import io.estatico.newtype.ops.toCoercibleIdOps
import skunk.Codec
import skunk.codec.all.{text, timestamp}

import java.time.LocalDateTime

package object db {

  final val createdAt: Codec[LocalDateTime] = timestamp(3)

  final val nonEmptyText: Codec[NonEmptyString] =
    text.imap[NonEmptyString](NonEmptyString.unsafeFrom)(_.value)

  implicit final val usernameCodec: skunk.Codec[UserName] = db.nonEmptyText.coerce[skunk.Codec[UserName]]

}
