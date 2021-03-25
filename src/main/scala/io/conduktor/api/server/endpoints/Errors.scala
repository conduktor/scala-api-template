package io.conduktor.api.server.endpoints


sealed trait ErrorInfo
case class NotFound(what: String) extends ErrorInfo
case object Unauthorized extends ErrorInfo
case class ServerError(msg: String) extends ErrorInfo
case object NoContent extends ErrorInfo
