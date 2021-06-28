package io.conduktor.api.http.v1

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait ErrorInfo
final case class NotFound(what: String)   extends ErrorInfo
final case class ServerError(msg: String) extends ErrorInfo
final case class Conflict(msg: String) extends ErrorInfo
case object Unauthorized                  extends ErrorInfo
case object NoContent                     extends ErrorInfo

object NotFound {
  implicit final val codec: Codec[NotFound] = deriveCodec
}

object Conflict {
  implicit final val codec: Codec[Conflict] = deriveCodec
}

object ServerError {
  implicit final val codec: Codec[ServerError] = deriveCodec
}
