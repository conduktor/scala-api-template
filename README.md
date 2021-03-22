# scala-api-template

- CRUD (http4s, tapir, circe)
- Listen (native postgres => stream => websocket)
- JWT validation against auth0
- Postgres, native, migrations with Prisma
- graalVM
- ZIO (effect, stream, config, tests)

## CAVEATS

Intellij can't type properly macros (such as skunk's "sql" StringOps)
Using Metals is therefore recommended when dealing with repositories


## TODOS
- ci
- tagged types with codecs (server, db)
- tests
- logging
- domain-specific error fmk
- streaming endpoints (paginated, listen+websocket)