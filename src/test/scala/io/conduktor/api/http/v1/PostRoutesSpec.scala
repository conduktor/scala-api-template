package io.conduktor.api.http.v1

import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.model.StatusCode
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, _}
import io.conduktor.api.BootstrapServer
import zio.magic.ZioProvideMagicOps
import zio.ZIO
import io.conduktor.api.http.Server

object PostRoutesSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Any] = suite("/v1/posts")(
    testM("POST / should return 200") {
      val payload = """{
                      | "title": "my test",
                      | "content": "blabla"
                      | }""".stripMargin
      (for {
        server   <- ZIO.service[Server.Server]
        client   <- HttpClientZioBackend()
        response <- basicRequest.body(payload).auth.bearer("Foo").post(uri"${server.baseUri}/v1/posts").send(client)
        _         = println(response.body.toString())
      } yield assert(response.code)(equalTo(StatusCode.Ok))).provideCustomMagicLayer(BootstrapServer.localServer)
    }
  )

}
