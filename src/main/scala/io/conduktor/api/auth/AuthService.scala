package io.conduktor.api.auth

import com.auth0.jwk.UrlJwkProvider
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.conduktor.api.config.Auth0Config
import io.conduktor.api.types.UserName
import pdi.jwt.{JwtAlgorithm, JwtBase64, JwtCirce, JwtClaim}
import zio.{Has, _}
import zio.clock.Clock

import java.time.{Instant, OffsetDateTime, ZoneId}

final case class User(name: UserName)

trait AuthService {
  def auth(token: String): Task[User]
}

object AuthService {
  def auth(token: String): ZIO[Has[AuthService], Throwable, User] = ZIO.serviceWith(_.auth(token))
}

final class JwtAuthService(auth0Conf: Auth0Config, clock: Clock.Service) extends AuthService {

  private val supportedAlgorithms = Seq(JwtAlgorithm.RS256)
  
  implicit val userCodec: Codec[User] = deriveCodec

  private def validateJwt(token: String): ZIO[Clock, Throwable, JwtClaim] = for {
    jwk    <- getJwk(token) // Get the secret key for this token

    claims <-
      ZIO.fromTry(JwtCirce.decode(token, jwk.getPublicKey, supportedAlgorithms)) // Decode the token using the secret key
    _      <- validateClaims(claims) // validate the data stored inside the token
  } yield claims

  private val splitToken = (jwt: String) =>
    jwt match {
      case s"$header.$body.$sig" => ZIO.succeed((header, body, sig))
      case _                     => ZIO.fail(new Exception("Token does not match the correct pattern"))
    }

  private val decodeElements = (data: Task[(String, String, String)]) =>
    data map { case (header, body, sig) =>
      (JwtBase64.decodeString(header), JwtBase64.decodeString(body), sig)
    }

  private def getJwk(token: String) =
    (splitToken andThen decodeElements)(token) flatMap { case (header, _, _) =>
      val jwtHeader   = JwtCirce.parseHeader(header)
      val jwkProvider = new UrlJwkProvider(s"https://${auth0Conf.domain}")
      jwtHeader.keyId.map { k =>
        Task(jwkProvider.get(k))
      } getOrElse ZIO.fail(new Exception("Unable to retrieve kid"))
    }

  private def clockFromOffset(now: OffsetDateTime): java.time.Clock = new java.time.Clock {
    override def getZone: ZoneId = now.getOffset

    override def withZone(zone: ZoneId): java.time.Clock = clockFromOffset(now.atZoneSameInstant(zone).toOffsetDateTime)

    override def instant(): Instant = now.toInstant
  }

  private val withJavaClock: ZIO[Clock, Throwable, java.time.Clock] = zio.clock.currentDateTime.map {
    clockFromOffset
  }

  private val validateClaims = (claims: JwtClaim) =>
    withJavaClock.flatMap { implicit clock =>
      if (claims.isValid(s"https://${auth0Conf.domain}/")) {
        ZIO.succeed(claims)
      } else {
        ZIO.fail(new Exception("The JWT did not pass validation"))
      }
    }

  override def auth(token: String): Task[User] =
    (for {
      bearer <- ZIO.fromOption {
                  token match {
                    case s"Bearer $a" => Some(a)
                    case _            => None
                  }
                }.orElseFail(new Throwable("Invalid auth header"))
      claims <- validateJwt(bearer)
      json   <- ZIO.fromEither(io.circe.parser.parse(claims.content))
      user   <- ZIO.fromEither(json.as[User])
    } yield user)
      //log
      .provide(Has(clock))

}

object JwtAuthService {
  val layer: URLayer[Has[Auth0Config] with Clock, Has[AuthService]] = (new JwtAuthService(_, _)).toLayer
}
