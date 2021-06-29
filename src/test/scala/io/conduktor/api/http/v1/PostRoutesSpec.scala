package io.conduktor.api.http.v1

import io.conduktor.api.BootstrapServer
import io.conduktor.api.http.Server
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

  val httpClient: ZLayer[Any, Throwable, Has[HttpClient]] = HttpClientZioBackend().toLayer

  override def spec: ZSpec[TestEnvironment, Throwable] = suite("/v1/posts")(
    testM("POST / should return 200") {
      val payload =
        """{
          | "title": "my test",
          | "content": "blabla"
          | }""".stripMargin
      for {
        server   <- ZIO.service[Server.Server]
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
        server   <- ZIO.service[Server.Server]
        client   <- ZIO.service[HttpClient]
        _        <- basicRequest.body(payload).auth.bearer("Foo").post(uri"${server.baseUri}/v1/posts").send(client)
        response <- basicRequest.auth.bearer("Foo").get(uri"${server.baseUri}/v1/posts").send(client)
      } yield assert(response.code)(equalTo(StatusCode.Ok)) && assert(response.body)(isRight(containsString("blabla")))
    }
  ).provideSomeLayerShared((httpClient ++ BootstrapServer.localServer).orDie) @@ sequential

}
