package io.conduktor.api.config

import zio.config.magnolia.DeriveConfigDescriptor._
import zio.config.typesafe._
import zio.config._
import zio.{ Has, Layer }

case class AppConfig(db: DBConfig, auth0: Auth0Config)
case class DBConfig(user: String, password: Option[String], host: String, port: Int, database: String, maxPoolSize: Int, additionalSourceProperties: Map[String, String])
case class Auth0Config(domain: String, audience: String)

object AppConfig {
  private val configDesc: ConfigDescriptor[AppConfig] = descriptor[AppConfig]
  val layer: Layer[ReadError[String], Has[AppConfig]] = TypesafeConfig.fromDefaultLoader[AppConfig](configDesc)
}
