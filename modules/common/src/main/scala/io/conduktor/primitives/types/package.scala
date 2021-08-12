package io.conduktor.primitives

import eu.timepit.refined.types.all.NonEmptyString
import io.estatico.newtype.macros.newtype

package object types {
  @newtype final case class Email(value: NonEmptyString) {
    def getUserName: Either[String, UserName] = value.value match {
      case s"$name@$_" => NonEmptyString.from(name).map(UserName.apply)
      case _           => Left("No username")
    }
  }
  @newtype final case class UserName(value: NonEmptyString)

  @newtype final case class Secret(_secret: NonEmptyString) {
    def unwrapValue: String       = _secret.value
    override def toString: String = "*****"
  }
}
