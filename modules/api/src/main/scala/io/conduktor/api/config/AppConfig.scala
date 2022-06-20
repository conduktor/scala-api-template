package io.conduktor.api.config

import eu.timepit.refined.predicates.all.NonEmpty
import io.conduktor.primitives.types.Secret
import io.estatico.newtype.ops.toCoercibleIdOps

import zio.config.ConfigDescriptor._
import zio.config._
import zio.config.refined._
import zio.{Has, ZLayer, system}

final case class AppConfig(
  db: DBConfig,
  http: HttpConfig,
  auth0: Auth0Config
)
final case class HttpConfig(port: Int)

private object DB {
  val config: ConfigDescriptor[DBConfig] =
    (string("DB_USER") zip
      refine[String, NonEmpty]("DB_PASSWORD").coerce[ConfigDescriptor[Secret]].optional zip
      string("DB_HOST") zip
      int("DB_PORT") zip
      string("DB_DATABASE") zip
      int("DB_MAX_POOL_SIZE") zip
      string("INSTANCE_CONNECTION_NAME").optional zip
      string("DB_BASELINE_VERSION").optional zip
      boolean("DB_MIGRATION").default(false) zip
      boolean("DB_USE_SSL").default(false)).to[DBConfig]

  val layer: ZLayer[system.System, ReadError[String], Has[DBConfig]] = ZConfig.fromSystemEnv(config)
}

private object Auth0 {
  val config: ConfigDescriptor[Auth0Config] =
    (
      refine[String, NonEmpty]("AUTH0_DOMAIN") zip
        refine[String, NonEmpty]("AUTH0_AUDIENCE").optional
    ).to[Auth0Config]
}
private object Http  {
  val config: ConfigDescriptor[HttpConfig] = int("PORT").default(8080).to[HttpConfig]
}

object AppConfig {
  type HasAllConfigs = Has[Auth0Config] with Has[DBConfig] with Has[HttpConfig]

  private val configDesc: ConfigDescriptor[AppConfig] = (
    (DB.config zip
      Http.config zip
      Auth0.config).to[AppConfig]
  )

  val dbOnlyLayer: ZLayer[system.System, ReadError[String], Has[DBConfig]] = DB.layer
  val layer: ZLayer[system.System, ReadError[String], Has[AppConfig]]      =
    ZConfig.fromSystemEnv(configDesc)

  // for layer config granularity
  val allLayers: ZLayer[system.System, ReadError[String], HasAllConfigs] =
    layer.to(
      ZLayer.fromServiceMany[AppConfig, HasAllConfigs] { case AppConfig(db, http, auth0) =>
        Has(db) ++ Has(http) ++ Has(auth0)
      }
    )
}
