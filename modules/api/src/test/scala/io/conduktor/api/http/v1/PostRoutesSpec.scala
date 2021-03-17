package io.conduktor.api.http.v1

import io.conduktor.api.ServerTestLayers.{dummyAuth, localDB, randomPortHttpConfig}
import io.conduktor.api.auth.User
import io.conduktor.api.http.Server
import io.conduktor.api.http.Server.Server
import io.conduktor.api.model.Post
import io.conduktor.api.service.PostService
import io.conduktor.api.{ApiTemplateApp, MemoryRepositorySpec}
import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.model.StatusCode
import zio.magic._
import zio.random.Random
import zio.test.Assertion.{containsString, equalTo, isRight}
import zio.test.TestAspect.sequential
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, _}
import zio.{Function0ToLayerSyntax, Has, RLayer, Task, TaskLayer, ULayer, ZIO, ZLayer}

import scala.collection.mutable

private class Stub extends PostService {
  private val posts = mutable.Map.empty[Post.Id, Post]

  override def createPost(user: User, title: Post.Title, content: Post.Content): ZIO[Random, PostService.CreatePostError, Post] = {
    for {
      random <- ZIO.service[Random.Service]
      uuid <- random.nextUUID
    } yield {
      val id = Post.Id(uuid)
      posts(id) = Post(
        id = id,
        title = title,
        author = user,
        published = false,
        content = content
      )
      posts(id)
    }
  }

  override def deletePost(uuid: Post.Id): Task[Unit] = Task(posts.remove(uuid)).unit

  override def findById(uuid: Post.Id): Task[Post] = Task(posts(uuid))

  override def all: Task[List[Post]] = Task(posts.values.toList)
}

private object Stub {
  val layer: ULayer[Has[PostService]] = (() => new Stub).toLayer
}

object PostRoutesSpec extends DefaultRunnableSpec {

  type HttpClient = SttpBackend[Task, ZioStreams with capabilities.WebSockets]
  type Env        = Has[HttpClient] with Has[Server]
  type Layer      = RLayer[zio.ZEnv, Env]

  val httpClient: TaskLayer[Has[HttpClient]] = HttpClientZioBackend().toLayer

  val commonLayers = ZLayer.fromSomeMagic[zio.ZEnv with Has[PostService], Env](
    httpClient,
    dummyAuth,
    randomPortHttpConfig,
    Server.layer
  )

  val dbLayers: Layer = ZLayer.fromSomeMagic[zio.ZEnv, Env](
      localDB,
      ApiTemplateApp.dbLayer,
      ApiTemplateApp.serviceLayer,
      commonLayers
    )

  val memoryLayer: Layer = ZLayer.fromSomeMagic[zio.ZEnv, Env](
      MemoryRepositorySpec.testLayer,
      ApiTemplateApp.serviceLayer,
      commonLayers
    )

  val stubServicesLayer: Layer = ZLayer.fromSomeMagic[zio.ZEnv, Env](
      Stub.layer,
      commonLayers
    )

  private case class TestEnv(name: String, layer: Layer)

  private val envs: Seq[TestEnv] = Seq(
    TestEnv("with db", dbLayers),
    TestEnv("with memory repository", memoryLayer),
    TestEnv("with stub services", stubServicesLayer)
  )

  private val suites: Seq[String => ZSpec[Env, Throwable]] = Seq(`/v1/posts`)

  private def run(envs: Seq[TestEnv], suites: Seq[String => ZSpec[Env, Throwable]]): ZSpec[zio.ZEnv, Throwable] =
    suite("")(envs.flatMap(env => suites.map(suite => suite(env.name).provideSomeLayerShared(env.layer.orDie))): _*)

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
