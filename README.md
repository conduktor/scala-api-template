# scala-api-template

A template of API we'll use at Conduktor, to work with this template of Front-end: https://github.com/conduktor/react-app-template.


## Requirements / Tech

- CRUD (http4s, circe, tapir)
- OpenApi exposed via tapir
- JWT validation against auth0
- Postgres (async, not jdbc) via Skunk, migrations with Flyway Migrate
- ZIO (effect, stream, config, tests)

## Development flow
- tag to deploy to production
- push main to deploy to staging

The stack is deployed on Google Cloud (Cloud Run + Cloud SQL)


## Database
The database layer is done via [Skunk](https://tpolecat.github.io/skunk/index.html) (using native postgres, not jdbc)

(TODO use skunk to validate db schema against compiled queries as of integration tests)

The database is hosted at CloudSQL. It can only be accessed via the CloudSQL proxy, behind the VPC, or by whitelisting an ip.

We use the VPC option (the proxy option does not fit well with Skunk config model)


## Migration
Database provisioning / migration is done via [Flyway](https://flywaydb.org/)
It is schema based (found in /src/main/resources/db/migration/).

We try to do only **backward compatible** changes. That allows us to simplify the integration (no downtime), allow rollbacks and prevent any data loss.

To run migration when the server is starting up, you have to enabled the migration flag env (DB_MIGRATION=true). It's disabled by default.

You can also run the migration with sbt :

`sbt migrate-apply` : apply migrations to your database

## Auth

Auth is simply a JWT validation + data extraction, against an auth0 tenant.

We just retrieve the exposed public key from auth0, and use it to validate et decode the token provided by the frontend as a Bearer Authorization header


## ISSUES

 - Intellij can't type properly macros (such as skunk's "sql" StringOps)
Using Metals is therefore recommended when dealing with repositories

## TODOS
- tagged types with codecs (server, db)
- tests (+ integration test with the DB to run in the CI)
- domain-specific error fmk
- streaming endpoints (paginated, websocket)
- db migration in the CI (requires a cloudSQL proxy step)
