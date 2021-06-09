package io.conduktor.api.server.endpoints

sealed trait ErrorInfo
final case class NotFound(what: String)   extends ErrorInfo
case object Unauthorized            extends ErrorInfo
final case class ServerError(msg: String) extends ErrorInfo
case object NoContent               extends ErrorInfo
