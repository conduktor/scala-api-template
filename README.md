<img src="https://www.conduktor.io/uploads/conduktor.svg" width="256">

# Conduktor's Scala API template

A template for writing Restful APIs we use at Conduktor.

## Requirements 

The requirements are:

- store data into Postgres
- handle Postgres schema and schema migration programmatically
- expose domain logic via a RESTful API
- describe the RESTful API using OpenAPI standard
- secure the RESTful API with JWT using auth0 service (https://auth0.com) but avoid vendor lock-in
- use only non-blocking technologies and implement only stateless services to handle high-scale workload
- leverage Scala type-system and hexagonal architecture to minimize the testing requirements
- enable testing at every layer: domain logic, end-to-end, data-access, RESTful API, integration of various layer combinations
- enforce green tests and style conformance using Github actions
- generate a docker image using Github actions and push it to a repository
- allows developing proprietary software

## Tech

This is the list of technologies we chose to implement our requirements:

- http4s (https://http4s.org/) for HTTP implementation, APL v2 license
- circe (https://circe.github.io/circe/) for JSON serialization, APL v2 license
- tapir (https://tapir.softwaremill.com) for RESTful API description, APL v2 license
- skunk (https://tpolecat.github.io/skunk/) for async (non-jdbc) Postgres access, MIT license
- flyway (https://flywaydb.org/) for database schema handling, APL v2 license
- zio (https://zio.dev/) for effect, streaming, logging, config and tests, APL v2 license
- JWT validation using auth0-provided Java library, MIT license
- refined (https://github.com/fthomas/refined) for defining value objects constraints, MIT license
- newtype (https://github.com/estatico/scala-newtype) for generating value objects with no runtime cost, APL v2 license
- sbt-native-packager (https://sbt-native-packager.readthedocs.io) for docker image generation, BSD-2-Clause License

## Development flow

- create branches from main
- merge into main to release Staging via Github action
- tag main to release Prod via Github action

The stack is deployed on Google Cloud (Cloud Run + Cloud SQL)


## Migration
Database provisioning / migration is done via [flyway](https://flywaydb.org/)

The migrations are applied at application start to ensure the database is up-to-date with current running code.


## Auth

Auth is a JWT validation + data extraction, against an auth0 tenant.

We retrieve the exposed public key from auth0, and use it to validate and decode the bearer token provided in the authorization header 


## ISSUES

 - Intellij can't type properly skunk's "sql" StringOps macro,
using Metals is therefore recommended when dealing with repositories

## TODOS
- use domain-specific errors
- add streaming endpoints examples (paginated, websocket)
