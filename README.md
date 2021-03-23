# scala-api-template

- CRUD (http4s, tapir, circe)
- Listen (native postgres => stream => websocket)
- JWT validation against auth0
- Postgres, native, migrations with Prisma
- graalVM
- ZIO (effect, stream, config, tests)

## Git flow 
- branch from develop
- merge into develop to release Staging
- merge develop into main to release Prod


## CAVEATS

 - Intellij can't type properly macros (such as skunk's "sql" StringOps)
Using Metals is therefore recommended when dealing with repositories

- many libraries are not compatible at all with graalVM. (ex: google cloud logback)
Also all classes using reflection must be added to reflect-config.json (ex: all GCP classes)

## TODOS
- tagged types with codecs (server, db)
- tests (+ integration test with the DB to run in the CI)
- logging to stackdriver via either [stdout](https://github.com/micronaut-projects/micronaut-gcp/blob/master/gcp-logging/src/main/java/io/micronaut/gcp/logging/StackdriverJsonLayout.java) or 
[http](https://stackoverflow.com/questions/63091045/invalid-jwt-failed-audience-check-when-using-google-api-services-in-graalvm-n) 
- domain-specific error fmk
- streaming endpoints (paginated, listen+websocket ?)
- opaque jwt auth0