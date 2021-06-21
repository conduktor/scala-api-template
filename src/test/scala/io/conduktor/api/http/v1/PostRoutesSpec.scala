package io.conduktor.api.http.v1

import io.conduktor.api.MemoryRepositorySpec
import io.conduktor.api.auth.{AuthService, User}
import io.conduktor.api.http.Server
import io.conduktor.api.service.{PostService, PostServiceLive}
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.model.{StatusCode, Uri}
import zio.clock.Clock
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, _}
import zio.{Has, Task, ULayer, ZIO, ZLayer, system}
import eu.timepit.refined.auto._
import io.conduktor.api.http.Server.Server
import io.conduktor.api.types.UserName

object PostRoutesSpec extends DefaultRunnableSpec {

  val dummyAuthServer: ULayer[Has[AuthService]] = ZLayer.succeed((_: String) =>
    Task {
      User(UserName("bob"))
    }
  )

  val service: ULayer[Has[PostService]] = MemoryRepositorySpec.testLayer >>> PostServiceLive.layer

  val server: ZLayer[system.System with Clock, Nothing, Has[Server]] =
    (ZLayer.identity[system.System] ++ ZLayer.identity[Clock] ++ dummyAuthServer ++ service) >>> Server.layer.orDie

  override def spec: ZSpec[TestEnvironment, Nothing] = suite("/v1/posts")(
    testM("POST / should return 200") {
      val payload = """{
                      | "title": "my test",
                      | "content": "blabla"
                      | }""".stripMargin
      for {
        server   <- ZIO.service[Server]
        client   <- HttpClientZioBackend().orDie
        request = basicRequest.body(payload).post(Uri.parse(server.baseUri.toString()).getOrElse(uri"").addPath("v1", "posts"))
        _        <- ZIO.debug(request.toString())
        response <-
          request.send(client).orDie
        _        <- ZIO.debug(response.toString())
      } yield assert(response.code)(equalTo(StatusCode.Ok))
    }.provideCustomLayer(server)
  )

}
