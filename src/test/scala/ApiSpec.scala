import eu.timepit.refined.auto._

import zio.test.RunnableSpec
import zio.test.TestAspect
import zio.test.TestRunner
import zio.duration._
import zio.test.environment.TestEnvironment
import zio.test._
import zio.ZIO

abstract class ApiSpec[Env <: TestEnvironment] extends RunnableSpec[Env, Any] {

  def testLayer: zio.Layer[Nothing, Environment]

  override def aspects: List[TestAspect[Nothing, Environment, Nothing, Any]] =
    List(TestAspect.timeoutWarning(60.seconds))

  override def runner: TestRunner[Environment, Any] =
    TestRunner(TestExecutor.default(testLayer))

  /**
   * Builds a suite containing a number of other specs.
   */
  def suite[R, E, T](label: String)(specs: Spec[R, E, T]*): Spec[R, E, T] =
    zio.test.suite(label)(specs: _*)

  /**
   * Builds an effectual suite containing a number of other specs.
   */
  def suiteM[R, E, T](label: String)(specs: ZIO[R, E, Iterable[Spec[R, E, T]]]): Spec[R, E, T] =
    zio.test.suiteM(label)(specs)

  /**
   * Builds a spec with a single pure test.
   */
  def test(label: String)(assertion: => TestResult)(implicit loc: SourceLocation): ZSpec[Any, Nothing] =
    zio.test.test(label)(assertion)

  /**
   * Builds a spec with a single effectful test.
   */
  def testM[R, E](label: String)(assertion: => ZIO[R, E, TestResult])(implicit loc: SourceLocation): ZSpec[R, E] =
    zio.test.testM(label)(assertion)
}
