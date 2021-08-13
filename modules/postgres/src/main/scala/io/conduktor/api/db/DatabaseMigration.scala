package io.conduktor.api.db

import scala.util.chaining.scalaUtilChainingOps

import io.conduktor.api.config.DBConfig
import org.flywaydb.core.Flyway

import zio._

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
      .dataSource(
        s"jdbc:postgresql://${config.host}:${config.port}/${config.database}",
        config.user,
        config.password.map(_.unwrapValue).orNull
      )
      .pipe(fw =>
        config.baselineVersion.fold(fw)(
          fw.baselineOnMigrate(true)
            .baselineVersion(_)
        )
      )
      .load()
      .migrate()
  }.unit

}

object FlywayDatabaseMigrationService {
  val layer: URLayer[Has[DBConfig], Has[DatabaseMigrationService]] = (new FlywayDatabaseMigrationService(_)).toLayer
}
