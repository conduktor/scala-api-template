package io.conduktor.api.config

import zio.config._
import ConfigDescriptor._
import zio.duration._
import zio.{Has, ZLayer, system}

final case class DBConfig(
  user: String,
  password: Option[String],
  host: String,
  port: Int,
  database: String,
  maxPoolSize: Int,
  gcpInstance: Option[String],
  ssl: Boolean = false,
  dbMigration: Boolean = false
)

object DBConfig {
  //doing manual mapping from env, graalvm was failing on hocon file with injected env
  val dbConfig: ConfigDescriptor[DBConfig] =
    (
      string("DB_USER") |@|
        string("DB_PASSWORD").optional |@|
        string("DB_HOST") |@|
        int("DB_PORT") |@|
        string("DB_DATABASE") |@|
        int("DB_MAX_POOL_SIZE") |@|
        string("INSTANCE_CONNECTION_NAME").optional |@|
        boolean("DB_USE_SSL").default(false) |@|
        boolean("DB_MIGRATION").default(false)
    )(DBConfig.apply, DBConfig.unapply)

  val layer: ZLayer[system.System, ReadError[String], Has[DBConfig]] = ZConfig.fromSystemEnv(dbConfig)
}

final case class Auth0Config(domain: String, audience: Option[String]) {
  val cacheSize: Int = 100
  val ttl: Duration = 10.hours
}

final case class HttpConfig(port: Int)

object AppConfig {
  type ConfEnv = Has[Auth0Config] with Has[DBConfig] with Has[HttpConfig]

  private val auth0Config: ConfigDescriptor[Auth0Config] =
    (
      string("AUTH0_DOMAIN") |@|
        string("AUTH0_AUDIENCE").optional
    )(Auth0Config.apply, Auth0Config.unapply)

  private val httpConfig: ConfigDescriptor[HttpConfig] = int("PORT").default(8080)(HttpConfig.apply, HttpConfig.unapply)

  val layer: ZLayer[system.System, ReadError[String], ConfEnv] =
    ZConfig.fromSystemEnv(auth0Config) ++ ZConfig.fromSystemEnv(httpConfig) ++ ZConfig.fromSystemEnv(DBConfig.dbConfig)
}
