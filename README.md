# scala-api-template

A template of API we'll use at Conduktor, to work with this template of Front-end: https://github.com/conduktor/react-app-template.


## Requirements / Tech

- CRUD (http4s, circe, tapir)
- OpenApi exposed via tapir
- JWT validation against auth0 
- Postgres (async, not jdbc) via Skunk, migrations with Prisma Migrate
- ZIO (effect, stream, config, tests)

## Development flow
- Do branches from develop
- Merge into develop to release Staging via github action
- Merge develop into main to release Prod via github action

The stack is deployed on Google Cloud (Cloud Run + Cloud SQL)


## Database
The database layer is done via [Skunk](https://tpolecat.github.io/skunk/index.html) (using native postgres, not jdbc)

(TODO use skunk to validate db schema against compiled queries as of integration tests)

The database is hosted at CloudSQL. It can only be accessed via the CloudSQL proxy, behind the VPC, or by whitelisting an ip.

We use the VPC option (the proxy option does not fit well with Skunk config model)


## Migration
Database provisioning / migration is done via [Prisma Migrate](https://www.prisma.io/docs/concepts/components/prisma-migrate)
It is schema based (found in /prisma/prisma.schema).

When developing locally, you should have an env var DB_URL pointing to a local/hosting database. . Of course, never point to a prod database, that's the job of the CI. The user must have the writes to create a DB
(or you can create it yourself)

That's because Prisma (in dev mode) creates a temporary [shadow database](https://www.prisma.io/docs/concepts/components/prisma-migrate/shadow-database) to test the schema migration and detect errors

To do a migration, start by altering the schema file. Schema doc can be found [here](https://www.prisma.io/docs/concepts/components/prisma-schema)

We try to do only **backward compatible** changes. That allows us to simplify the integration (no downtime), allow rollbacks and prevent any data loss.

In that case, you have no SQL to write, Prisma will generate everything for you.

You can review (and alter) SQL deltas before pushing in /prisma/migrations. *Do not modify already applied deltas.*

You can also add your own deltas (to migrate data for example in the case of a non-backward-compatible migration)

Main commands integrated with sbt are :

`sbt migration create` : create a migration sql file from the changes in your schema

`sbt migration apply dev` : apply migrations to your database (and create migration sql file if needed)

`sbt migration validate` : validate your schema files

Other commands are available for edge cases (reset local migration history, introspect database to update the schema, push without creating migrations), see the [Prisma CLI](https://www.prisma.io/docs/reference/api-reference/command-reference/)


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
