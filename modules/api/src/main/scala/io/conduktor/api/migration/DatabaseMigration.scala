package io.conduktor.api.migration

import zio._
import io.conduktor.api.config.DBConfig
import org.flywaydb.core.Flyway

trait DatabaseMigrationService {
  def migrate(): Task[Unit]
}

object DatabaseMigrationService {
  def migrate(): ZIO[Has[DatabaseMigrationService], Throwable, Unit] = ZIO.serviceWith(_.migrate())
}

final class FlywayDatabaseMigrationService(config: DBConfig) extends DatabaseMigrationService {

  def migrate(): Task[Unit] = Task {
    Flyway
      .configure()
      .dataSource(s"jdbc:postgresql://${config.host}:${config.port}/${config.database}", config.user, config.password.orNull)
      .load()
      .migrate()
  }.unit

}

object FlywayDatabaseMigrationService {
  val layer: URLayer[Has[DBConfig], Has[DatabaseMigrationService]] = (new FlywayDatabaseMigrationService(_)).toLayer
}
