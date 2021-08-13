package io.conduktor.api.http

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait ErrorInfo
case object Unauthorized                extends ErrorInfo
case object Forbidden                   extends ErrorInfo
case object NoContent                   extends ErrorInfo
final case class NotFound(what: String) extends ErrorInfo
object NotFound    {
  implicit val notFoundCodec: Codec[NotFound] = deriveCodec
}
final case class Conflict(msg: String) extends ErrorInfo
object Conflict    {
  implicit val conflictCodec: Codec[Conflict] = deriveCodec
}
final case class BadRequest(msg: String) extends ErrorInfo
object BadRequest  {
  implicit val badRequestCodec: Codec[BadRequest] = deriveCodec
}
final case class ServerError(msg: String) extends ErrorInfo
object ServerError {
  implicit val serverErrorCodec: Codec[ServerError] = deriveCodec
}
