package io.conduktor.api.config

import zio.config._
import ConfigDescriptor._
import zio.{Has, ZLayer, system}

case class AppConfig(db: DBConfig, auth0: Auth0Config)
case class DBConfig(user: String, password: Option[String], host: String, port: Int, database: String, maxPoolSize: Int, gcpInstance: Option[String], ssl: Boolean = false)
case class Auth0Config(domain: String, audience: Option[String])

object AppConfig {

  //doing manual mapping from env, graalvm was failing on hocon file with injected env
  private val dbConfig: ConfigDescriptor[DBConfig] = (string("DB_USER") |@|
    string("DB_PASSWORD").optional |@|
    string("DB_HOST") |@|
    int("DB_PORT") |@|
    string("DB_DATABASE") |@|
    int("DB_MAX_POOL_SIZE") |@|
    string("INSTANCE_CONNECTION_NAME").optional |@|
    boolean("DB_USE_SSL").default(false)
    ) (DBConfig.apply, DBConfig.unapply)

  private val auth0Config: ConfigDescriptor[Auth0Config] =
    (string("AUTH0_DOMAIN") |@|
      string("AUTH0_AUDIENCE").optional
 ) (Auth0Config.apply, Auth0Config.unapply)

  private val configDesc: ConfigDescriptor[AppConfig] = (dbConfig |@| auth0Config) (AppConfig.apply, AppConfig.unapply)

  val layer: ZLayer[system.System, ReadError[String], Has[AppConfig]] = ZConfig.fromSystemEnv(configDesc)

}
