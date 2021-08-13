package io.conduktor.api.repository.db

import io.conduktor.api.repository.PostRepository.Error
import skunk.{Command, Query, Session}
import skunk.data.Completion
import zio.{IO, Managed, Task, TaskManaged}
import zio.interop.catz._

private[db] object SkunkExtensions {

  implicit final class ManagedOps[A](private val self: TaskManaged[A]) extends AnyVal {
    def wrapException: Managed[Error.Unexpected, A] = self.mapError(Error.Unexpected)
  }
  implicit final class ZioOps[A](private val self: Task[A])            extends AnyVal {
    def wrapException: IO[Error.Unexpected, A] = self.mapError(Error.Unexpected)
  }

  implicit final class CommandOps[A](private val self: Command[A])   extends AnyVal {
    def execute(a: A)(implicit session: Session[Task]): IO[Error.Unexpected, Completion] =
      session.prepare(self).use(_.execute(a)).wrapException
  }
  implicit final class QueryOps[A, B](private val self: Query[A, B]) extends AnyVal {
    def option(a: A)(implicit session: Session[Task]): IO[Error.Unexpected, Option[B]]             =
      session.prepare(self).use(_.option(a)).wrapException
    def unique(a: A)(implicit session: Session[Task]): IO[Error.Unexpected, B]                     = session.prepare(self).use(_.unique(a)).wrapException
    def list(a: A, chunkSize: Int)(implicit session: Session[Task]): IO[Error.Unexpected, List[B]] =
      session.prepare(self).use(_.stream(a, chunkSize).compile.toList).wrapException
  }
}
