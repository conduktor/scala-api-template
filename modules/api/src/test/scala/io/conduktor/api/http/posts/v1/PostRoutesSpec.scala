package io.conduktor.api.http.posts.v1

import scala.collection.mutable

import io.conduktor.api.ApiTemplateApp
import io.conduktor.api.ServerTestLayers.{dummyAuth, localDB, randomPortHttpConfig}
import io.conduktor.api.db.MemoryRepositorySpec
import io.conduktor.api.http.Server
import io.conduktor.api.model.Post
import io.conduktor.api.service.PostService
import io.conduktor.primitives.types.UserName
import org.http4s.server.Server
import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.model.StatusCode

import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.Logging
import zio.magic._
import zio.random.Random
import zio.test.Assertion.{containsString, equalTo, isRight}
import zio.test.TestAspect.sequential
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{Has, IO, RLayer, Task, TaskLayer, UIO, URLayer, ZIO, ZLayer}

private class Stub(random: Random.Service) extends PostService {
  private val posts = mutable.Map.empty[Post.Id, Post]

  override def createPost(author: UserName, title: Post.Title, content: Post.Content): IO[PostService.PostServiceError, Post] =
    for {
      uuid <- random.nextUUID
    } yield {
      val id = Post.Id(uuid)
      posts(id) = Post(
        id = id,
        title = title,
        author = author,
        published = false,
        content = content
      )
      posts(id)
    }

  override def deletePost(uuid: Post.Id): IO[PostService.PostServiceError, Unit] = UIO(posts.remove(uuid)).unit

  override def findById(uuid: Post.Id): IO[PostService.PostServiceError, Post] = UIO(posts(uuid))

  override def all: IO[PostService.PostServiceError, List[Post]] = UIO(posts.values.toList)
}

private object Stub {
  val layer: URLayer[Random, Has[PostService]] = (new Stub(_)).toLayer
}

object PostRoutesSpec extends DefaultRunnableSpec {

  type HttpClient = SttpBackend[Task, ZioStreams with capabilities.WebSockets]
  type Env        = Has[HttpClient] with Has[Server] with Logging
  type Layer      = RLayer[zio.ZEnv, Env]

  val httpClient: TaskLayer[Has[HttpClient]] = HttpClientZioBackend().toLayer

  val commonLayers: ZLayer[Has[Clock.Service]
    with Has[zio.console.Console.Service]
    with Has[zio.system.System.Service]
    with Has[Random.Service]
    with Has[
      Blocking.Service
    ]
    with Has[PostService], Throwable, Has[HttpClient] with Has[Server]] =
    ZLayer.fromSomeMagic[zio.ZEnv with Has[PostService], Env](
      httpClient,
      dummyAuth,
      randomPortHttpConfig,
      Server.layer,
      Logging.ignore
    )

  val dbLayers: Layer = ZLayer.fromSomeMagic[zio.ZEnv, Env](
    localDB,
    ApiTemplateApp.dbLayer,
    ApiTemplateApp.serviceLayer,
    commonLayers,
    Logging.ignore
  )

  val memoryLayer: Layer = ZLayer.fromSomeMagic[zio.ZEnv, Env](
    MemoryRepositorySpec.testLayer,
    ApiTemplateApp.serviceLayer,
    commonLayers,
    Logging.ignore
  )

  val stubServicesLayer: Layer = ZLayer.fromSomeMagic[zio.ZEnv, Env](
    Stub.layer,
    commonLayers,
    Logging.ignore
  )

  private final case class TestEnv(name: String, layer: Layer)

  private val envs: Seq[TestEnv] = Seq(
    TestEnv("with db", dbLayers),
    TestEnv("with memory repository", memoryLayer),
    TestEnv("with stub services", stubServicesLayer)
  )

  private val suites: Seq[String => ZSpec[Env, Throwable]] = Seq(`/posts/v1`)

  private def run(envs: Seq[TestEnv], suites: Seq[String => ZSpec[Env, Throwable]]): ZSpec[zio.ZEnv, Throwable] =
    suite("")(envs.flatMap(env => suites.map(suite => suite(env.name).provideSomeLayerShared(env.layer.orDie))): _*)

  override def spec: ZSpec[TestEnvironment, Throwable] = run(envs, suites)

  private def `/posts/v1`(name: String) = {
    suite(s"/posts/v1 $name")(
      testM("POST / should return 200") {
        val payload =
          """{
            | "title": "my test",
            | "content": "blabla"
            | }""".stripMargin
        for {
          server   <- ZIO.service[Server]
          client   <- ZIO.service[HttpClient]
          response <- basicRequest.body(payload).auth.bearer("Foo").post(uri"${server.baseUri}/posts/v1").send(client)
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
          _        <- basicRequest.body(payload).auth.bearer("Foo").post(uri"${server.baseUri}/posts/v1").send(client)
          response <- basicRequest.auth.bearer("Foo").get(uri"${server.baseUri}/posts/v1").send(client)
        } yield assert(response.code)(equalTo(StatusCode.Ok)) && assert(response.body)(isRight(containsString("blabla")))
      }
    )
  } @@ sequential

}
