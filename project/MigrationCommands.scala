import sbt._

object MigrationCommands {
  /*
    Prisma is a node CLI
    All we need is to have the node runtime installed on machines running below scripts (local, CI)
   */

  import sys.process._

  // validate schema
  def validateSchema: String = "npx prisma validate" !!

  // create migration
  def createMigration: String = "npx prisma migrate dev --create-only" !!

  // apply migration dev (create shallow db, test then apply)
  def applyMigrationDev: String = "npx prisma migrate dev" !!

  // get migration status
  def getMigrationStatus: String = "npx prisma migrate status" !!

  // apply migration prod. Used by CI/CD !
  def applyProd_danger: String = "npx prisma migrate deploy" !!

  // to solve issues : https://www.prisma.io/docs/reference/api-reference/command-reference/#migrate-resolve
  // forces either "rolled back" or "applied" status to failed migration

}
