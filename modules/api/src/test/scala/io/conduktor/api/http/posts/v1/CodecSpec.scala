package io.conduktor.api.http.posts.v1

import cats.Eq
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.testing.CodecTests
import io.circe.testing.instances.arbitraryJson
import io.conduktor.api.http.posts.v1.Domain.CreatePostInput
import org.scalacheck.Test.{Failed, Result}
import org.scalacheck.{Arbitrary, Gen, Test}
import org.typelevel.discipline.Laws

import zio.Task
import zio.test.Assertion.{anything, isSubtype, not}
import zio.test.{DefaultRunnableSpec, Spec, TestFailure, TestSuccess, assert}

object CodecSpec extends DefaultRunnableSpec {

  val createPostInputCodec: CodecTests[CreatePostInput] = CodecTests[CreatePostInput]

  object Implicits {
    implicit val eqCreatePostInput: Eq[CreatePostInput] = Eq.fromUniversalEquals

    val genNonEmptyString: Gen[NonEmptyString]                  = Gen.alphaStr.filter(_.nonEmpty).map(NonEmptyString.unsafeFrom)
    implicit val genCreatePostInput: Arbitrary[CreatePostInput] = Arbitrary {
      for {
        title   <- genNonEmptyString
        content <- Gen.alphaStr
      } yield CreatePostInput(title, content)
    }
  }

  import Implicits._

  val spec: Spec[Any, TestFailure[Throwable], TestSuccess] = suite("codecs should respect codec law")(
    checkLaw("CreatePostInput codec laws", createPostInputCodec.codec)
  )

  def checkLaw(testName: String, ruleSet: Laws#RuleSet): Spec[Any, TestFailure[Throwable], TestSuccess] =
    suite(testName)(
      ruleSet.all.properties.toList.map { case (id, prop) =>
        testM(s"$id $prop") {
          Task {
            Test.check(prop)(identity)
          }
            .map((result: Result) => assert(result.status)(not(isSubtype[Failed](anything))))
        }
      }: _*
    )

}
