package io.conduktor.api.service

import io.conduktor.api.model.Post
import io.conduktor.api.model.Post.{Content, Title}
import io.conduktor.api.repository
import io.conduktor.api.repository.PostRepository
import io.conduktor.api.service.PostService.{DuplicatePostError, PostServiceError}
import io.conduktor.primitives.types.{Email, UserName}
import zio._
import zio.logging.{Logger, Logging}
import zio.random.Random

trait PostService {
  def createPost(author: UserName, title: Title, content: Content): IO[PostServiceError, Post]

  def deletePost(id: Post.Id): IO[PostServiceError, Unit]

  def findById(id: Post.Id): IO[PostServiceError, Post]

  def all: IO[PostServiceError, List[Post]]
}

object PostService {
  sealed trait PostServiceError

  final case class RepositoryError(err: repository.PostRepository.Error) extends PostServiceError

  final case class InvalidEmail(email: Email) extends PostServiceError

  final case class DuplicatePostError(title: Title) extends PostServiceError

  final case class Unexpected(throwable: Throwable) extends PostServiceError
}

final class PostServiceLive(random: Random.Service, postRepositoryPool: PostRepository.Pool)(implicit logger: Logger[String])
    extends PostService {

  import PostServiceLive._

  override def createPost(author: UserName, title: Title, content: Content): IO[PostServiceError, Post] =
    for {
      id           <- random.nextUUID.map(Post.Id.apply)
      maybeCreated <- postRepositoryPool
                        .use(repo =>
                          repo
                            .findPostByTitle(title)
                            .flatMap(existing =>
                              existing
                                .map(_ => ZIO.none)
                                .getOrElse(repo.createPost(id, title, author, content).asSome)
                            )
                        )
                        .domainError
      created      <- maybeCreated match {
                        case Some(created) => ZIO.succeed(created)
                        case None          => ZIO.fail(DuplicatePostError(title))
                      }
    } yield created

  override def deletePost(id: Post.Id): IO[PostServiceError, Unit] = postRepositoryPool.use(_.deletePost(id)).domainError

  override def findById(id: Post.Id): IO[PostServiceError, Post] = postRepositoryPool.use(_.findPostById(id)).domainError

  override def all: IO[PostServiceError, List[Post]] = postRepositoryPool.use(_.allPosts).domainError
}

object PostServiceLive {

  implicit private[PostServiceLive] final class PostRepoErrorOps[A](val io: IO[PostRepository.Error, A]) {
    def domainError(implicit logger: Logger[String]): IO[PostService.RepositoryError, A] = io.tapError {
      case PostRepository.Error.Unexpected(throwable) => logger.throwable("Unexpected repository error", throwable)
      case _                                          => ZIO.unit
    }
      .mapError(PostService.RepositoryError.apply)
  }

  val layer: URLayer[Has[PostRepository.Pool] with Logging with Random, Has[PostService]] =
    ZLayer.fromServices[PostRepository.Pool, Logger[String], Random.Service, PostService] { (pool, log, rand) =>
      new PostServiceLive(rand, pool)(log)
    }
}
