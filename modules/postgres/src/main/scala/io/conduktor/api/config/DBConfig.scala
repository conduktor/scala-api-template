package io.conduktor.api.config

import io.conduktor.primitives.types.Secret

final case class DBConfig(
  user: String,
  password: Option[Secret],
  host: String,
  port: Int,
  database: String,
  maxPoolSize: Int,
  gcpInstance: Option[String],
  // flyway baseline migration. If 1, only migrations > V1 will apply. Should be None on new database
  baselineVersion: Option[String],
  migrate: Boolean,
  ssl: Boolean
)
