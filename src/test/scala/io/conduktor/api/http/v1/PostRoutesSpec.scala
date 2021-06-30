package io.conduktor.api.http.v1

import io.conduktor.api.config.{AppConfig, Auth0Config, DBConfig, HttpConfig}
import io.conduktor.api.http.Server
import io.conduktor.api.http.Server.Server
import io.conduktor.api.{ApiTemplateApp, BootstrapServer, MemoryRepositorySpec}
import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.model.StatusCode
import zio.test.Assertion.{containsString, equalTo, isRight}
import zio.test.TestAspect.sequential
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, _}
import zio.{Has, Task, ZIO, ZLayer}

object PostRoutesSpec extends DefaultRunnableSpec {

  type HttpClient = SttpBackend[Task, ZioStreams with capabilities.WebSockets]
  type Env        = Has[HttpClient] with Has[Server]
  type Layer      = ZLayer[zio.ZEnv, Nothing, Env]

  val httpClient: ZLayer[Any, Throwable, Has[HttpClient]] = HttpClientZioBackend().toLayer

  val dbLayers: Layer = (httpClient ++ BootstrapServer.localServer).orDie

  val memAppConf = ZLayer.succeed(
    AppConfig(
      DBConfig(
        user = "foo",
        password = None,
        host = "blabl",
        port = 22,
        database = "db",
        maxPoolSize = 11,
        gcpInstance = None,
        ssl = false
      ),
      Auth0Config("foo", None),
      HttpConfig(0)
    )
  )

  val memoryLayer: Layer = (httpClient ++ ((ZLayer.identity[
    zio.ZEnv
  ] ++ (MemoryRepositorySpec.testLayer >>> ApiTemplateApp.serviceLayer) ++ (memAppConf >>> ApiTemplateApp.httpConfig) ++ BootstrapServer.dummyAuth) >>> Server.layer)).orDie

  private case class TestEnv(name: String, layer: Layer)

  private val envs: Seq[TestEnv] = Seq(TestEnv("with db", dbLayers), TestEnv("with memory repository", memoryLayer))

  private val suites: Seq[String => ZSpec[Env, Throwable]] = Seq(`/v1/posts`)

  private def run(envs: Seq[TestEnv], suites: Seq[String => ZSpec[Env, Throwable]]) =
    suite("")(envs.flatMap(env => suites.map(suite => suite(env.name).provideSomeLayerShared(env.layer))): _*)

  override def spec: ZSpec[TestEnvironment, Throwable] = run(envs, suites)

  private def `/v1/posts`(name: String) = {
    suite(s"/v1/posts $name")(
      testM("POST / should return 200") {
        val payload =
          """{
            | "title": "my test",
            | "content": "blabla"
            | }""".stripMargin
        for {
          server   <- ZIO.service[Server]
          client   <- ZIO.service[HttpClient]
          response <- basicRequest.body(payload).auth.bearer("Foo").post(uri"${server.baseUri}/v1/posts").send(client)
        } yield assert(response.code)(equalTo(StatusCode.Ok))
      },
      testM("GET / should return a post") {
        val payload =
          """{
            | "title": "my test",
            | "content": "blabla"
            | }""".stripMargin
        for {
          server   <- ZIO.service[Server]
          client   <- ZIO.service[HttpClient]
          _        <- basicRequest.body(payload).auth.bearer("Foo").post(uri"${server.baseUri}/v1/posts").send(client)
          response <- basicRequest.auth.bearer("Foo").get(uri"${server.baseUri}/v1/posts").send(client)
        } yield assert(response.code)(equalTo(StatusCode.Ok)) && assert(response.body)(isRight(containsString("blabla")))
      }
    )
  } @@ sequential

}
