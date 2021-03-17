package io.conduktor.api.auth

import com.auth0.jwk.{Jwk, JwkProviderBuilder}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.conduktor.api.auth.JwtAuthService.{AuthException, BearerToken, Header}
import io.conduktor.api.config.Auth0Config
import io.conduktor.api.types.UserName
import pdi.jwt.{JwtAlgorithm, JwtBase64, JwtCirce, JwtClaim}
import zio.clock.Clock
import zio.{Has, _}
import io.estatico.newtype.macros.newtype

import java.time.{Instant, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit
import scala.annotation.nowarn

final case class User(name: UserName)

trait AuthService {
  def auth(token: String): Task[User]
}

object AuthService {
  def auth(token: String): RIO[Has[AuthService], User] = ZIO.serviceWith(_.auth(token))
}

final class JwtAuthService(auth0Conf: Auth0Config, clock: Clock.Service) extends AuthService {

  private val supportedAlgorithms = Seq(JwtAlgorithm.RS256)
  private val issuer              = s"https://${auth0Conf.domain}"

  private val cachedJwkProvider =
    new JwkProviderBuilder(issuer)
      .cached(auth0Conf.cacheSize.toLong, auth0Conf.ttl.toSeconds, TimeUnit.SECONDS)
      .build()

  private implicit val userCodec: Codec[User] = deriveCodec

  private def clockFromOffset(now: OffsetDateTime): java.time.Clock = new java.time.Clock {
    override def getZone: ZoneId = now.getOffset

    override def withZone(zone: ZoneId): java.time.Clock = clockFromOffset(now.atZoneSameInstant(zone).toOffsetDateTime)

    override def instant(): Instant = now.toInstant
  }

  private val withJavaClock: ZIO[Clock, Throwable, java.time.Clock] = zio.clock.currentDateTime.map {
    clockFromOffset
  }

  override def auth(token: String): Task[User] = {
    for {
      bearer <- extractBearer(token)
      claims <- validateJwt(bearer)
      user   <- ZIO.fromEither(decode[User](claims.content))
    } yield user
  }
    //log
    .provide(Has(clock))

  private def extractBearer(token: String): Task[BearerToken] =
    ZIO.fromOption {
      token match {
        case s"Bearer $a" => Some(BearerToken(a))
        case _            => None
      }
    }.orElseFail(new AuthException("Invalid auth header"))

  private def validateJwt(token: BearerToken): RIO[Clock, JwtClaim] = for {
    jwk    <- getJwk(token) // Get the secret key for this token
    claims <-
      ZIO.fromTry(JwtCirce.decode(token.value, jwk.getPublicKey, supportedAlgorithms)) // Decode the token using the secret key
    _      <- validateClaims(claims) // validate the data stored inside the token
  } yield claims

  private def getJwk(token: BearerToken): Task[Jwk] =
    for {
      header    <- extractHeader(token)
      jwtHeader <- Task(JwtCirce.parseHeader(header.value))
      kid       <- IO.fromOption(jwtHeader.keyId).orElseFail(new AuthException("Unable to retrieve kid"))
      jwk       <- Task(cachedJwkProvider.get(kid))
    } yield jwk

  @nowarn
  private def extractHeader(jwt: BearerToken): Task[Header] =
    jwt.value match {
      case s"$header.$body.$sig" => ZIO.succeed(Header(JwtBase64.decodeString(header)))
      case _                     => ZIO.fail(new AuthException("Token does not match the correct pattern"))
    }

  private def validateClaims(claims: JwtClaim) =
    withJavaClock.flatMap { implicit clock =>
      ZIO
        .fail(new RuntimeException("The JWT did not pass validation"))
        .unless(claims.isValid(issuer)(clock))
    }
}

object JwtAuthService {
  @newtype private case class BearerToken(value: String)
  @newtype private case class Header(value: String)

  class AuthException(message: String) extends RuntimeException(message)

  val layer: URLayer[Has[Auth0Config] with Clock, Has[AuthService]] = (new JwtAuthService(_, _)).toLayer
}
