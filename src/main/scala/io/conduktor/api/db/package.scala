package io.conduktor.api

import eu.timepit.refined.types.all.NonEmptyString
import skunk.Codec
import skunk.codec.all.text

package object db {

  final val nonEmptyText: Codec[NonEmptyString] =
    text.imap[NonEmptyString](NonEmptyString.unsafeFrom)(_.value)

}
