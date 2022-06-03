package io.conduktor.api.auth

import java.time.{Instant, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit

import com.auth0.jwk.{Jwk, JwkProviderBuilder}
import eu.timepit.refined.types.all.NonEmptyString
import io.circe.Decoder
import io.circe.parser.decode
import io.conduktor.api.auth.AuthService.AuthToken
import io.conduktor.api.auth.JwtAuthService.{AuthException, Header}
import io.conduktor.api.config.Auth0Config
import io.conduktor.api.model.User
import io.estatico.newtype.macros.newtype
import pdi.jwt.{JwtAlgorithm, JwtBase64, JwtCirce, JwtClaim}

import zio._
import zio.clock.Clock
import zio.logging.{Logger, Logging}

trait AuthService {
  def auth(token: AuthToken): Task[User]
}

object AuthService {
  @newtype final case class AuthToken(value: NonEmptyString) {
    def show: String = value.value
  }
  object AuthToken                                           {
    import io.circe.refined._
    import io.estatico.newtype.ops._
    implicit val authTokenDecoder: Decoder[AuthToken] = Decoder[NonEmptyString].coerce[Decoder[AuthToken]]
  }

  def auth(token: AuthToken): RIO[Has[AuthService], User] = ZIO.serviceWith(_.auth(token))
}

object JwtAuthService {
  @newtype private final case class Header(value: String)

  class AuthException(message: String) extends RuntimeException(message)

  val layer: URLayer[Has[Auth0Config] with Clock with Logging, Has[AuthService]] = (new JwtAuthService(_, _, _)).toLayer
}

final class JwtAuthService(auth0Conf: Auth0Config, clock: Clock.Service, log: Logger[String]) extends AuthService {

  private val supportedAlgorithms = Seq(JwtAlgorithm.RS256)
  private val issuer              = s"https://${auth0Conf.domain}/"

  private val cachedJwkProvider =
    new JwkProviderBuilder(issuer)
      .cached(auth0Conf.cacheSize.toLong, auth0Conf.ttl.getSeconds, TimeUnit.SECONDS)
      .build()

  private def clockFromOffset(now: OffsetDateTime): java.time.Clock = new java.time.Clock {
    override def getZone: ZoneId = now.getOffset

    override def withZone(zone: ZoneId): java.time.Clock = clockFromOffset(now.atZoneSameInstant(zone).toOffsetDateTime)

    override def instant(): Instant = now.toInstant
  }

  private val withJavaClock: ZIO[Clock, Throwable, java.time.Clock] = zio.clock.currentDateTime.map {
    clockFromOffset
  }

  override def auth(token: AuthToken): Task[User] = {
    for {
      claims <- validateJwt(token)
      user   <- ZIO.fromEither(decode[User](claims.content))
    } yield user
  }
    .tapError(log.throwable("Failed to parse auth token", _))
    .provide(Has(clock))

  private def validateJwt(token: AuthToken): RIO[Clock, JwtClaim] = for {
    jwk    <- getJwk(token)          // Get the secret key for this token
    claims <-
      ZIO.fromTry(JwtCirce.decode(token.show, jwk.getPublicKey, supportedAlgorithms)) // Decode the token using the secret key
    _      <- validateClaims(claims) // validate the data stored inside the token
  } yield claims

  private def getJwk(token: AuthToken): Task[Jwk] =
    for {
      header    <- extractHeader(token)
      jwtHeader <- Task(JwtCirce.parseHeader(header.value))
      kid       <- IO.fromOption(jwtHeader.keyId).orElseFail(new AuthException("Unable to retrieve kid"))
      jwk       <- Task(cachedJwkProvider.get(kid))
    } yield jwk

  private def extractHeader(jwt: AuthToken): Task[Header] =
    jwt.show match {
      case s"$header.$_.$_" => ZIO.succeed(Header(JwtBase64.decodeString(header)))
      case _                => ZIO.fail(new AuthException("Token does not match the correct pattern"))
    }

  private def validateClaims(claims: JwtClaim) =
    withJavaClock.flatMap { implicit clock =>
      ZIO
        .fail(new RuntimeException(s"The JWT did not pass validation for issuer $issuer"))
        .unless(claims.isValid(issuer)(clock))
    }
}
