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
    (string("DB_USER") |@|
      refine[String, NonEmpty]("DB_PASSWORD").coerce[ConfigDescriptor[Secret]].optional |@|
      string("DB_HOST") |@|
      int("DB_PORT") |@|
      string("DB_DATABASE") |@|
      int("DB_MAX_POOL_SIZE") |@|
      string("INSTANCE_CONNECTION_NAME").optional |@|
      string("DB_BASELINE_VERSION").optional |@|
      boolean("DB_MIGRATION").default(false) |@|
      boolean("DB_USE_SSL").default(false)).to[DBConfig]

  val layer: ZLayer[system.System, ReadError[String], Has[DBConfig]] = ZConfig.fromSystemEnv(config)
}

private object Auth0 {
  val config: ConfigDescriptor[Auth0Config] =
    (
      refine[String, NonEmpty]("AUTH0_DOMAIN") |@|
        refine[String, NonEmpty]("AUTH0_AUDIENCE").optional
    )(Auth0Config.apply, Auth0Config.unapply)
}
private object Http  {
  val config: ConfigDescriptor[HttpConfig] = int("PORT").default(8080)(HttpConfig.apply, HttpConfig.unapply)
}

object AppConfig {
  type HasAllConfigs = Has[Auth0Config] with Has[DBConfig] with Has[HttpConfig]

  private val configDesc: ConfigDescriptor[AppConfig] = (
    (DB.config |@|
      Http.config |@|
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
