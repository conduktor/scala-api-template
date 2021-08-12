package io.conduktor.api.config

import eu.timepit.refined.types.string.NonEmptyString

import zio.duration.{Duration, durationInt}

final case class Auth0Config(domain: NonEmptyString, audience: Option[NonEmptyString]) {
  val cacheSize: Int = 100
  val ttl: Duration  = 10.hours
}
