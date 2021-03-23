package io.conduktor.api.auth


import java.time.{Instant, OffsetDateTime, ZoneId}

import com.auth0.jwk.UrlJwkProvider
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.conduktor.api.config.Auth0Config
import pdi.jwt.{JwtAlgorithm, JwtBase64, JwtCirce, JwtClaim}

import zio._
import zio.clock.Clock

object UserAuthenticationLayer {
  type AuthService = Has[AuthService.Service]

  case class User(name: String)

  object User {
    implicit val userCodec: Codec[User] = deriveCodec
  }


  object AuthService {

    trait Service {
      def auth(token: String): Task[User]
    }

    val live: ZLayer[Has[Auth0Config] with Clock, Nothing, Has[Service]] = ZLayer.fromServices[Auth0Config, Clock.Service, Service] { // [Auth0Config, Clock.Service, Has[Service]]
     case (auth0Conf: Auth0Config, clock: Clock.Service) =>


        val service: Service = new Service {

        def auth(token: String): Task[User] = {
          (for {
            bearer <-    ZIO.fromOption {
              token match {
                case s"Bearer $a" => Some(a)
                case _ => None
              }
            }.mapError(_ => new Throwable("Invalid auth header"))
            claims <- validateJwt(bearer)
            json <- ZIO.fromEither(io.circe.parser.parse(claims.content))
            user <- ZIO.fromEither(json.as[User])
          } yield user
            )
            //log
            .provide(Has(clock))
        }

        // extract the header, claims and signature
        private val jwtRegex = """(.+?)\.(.+?)\.(.+?)""".r

        private def validateJwt(token: String): ZIO[Clock, Throwable, JwtClaim] = for {
          jwk <- getJwk(token) // Get the secret key for this token
          claims <- ZIO.fromTry(JwtCirce.decode(token, jwk.getPublicKey, Seq(JwtAlgorithm.RS256))) // Decode the token using the secret key
          _ <- validateClaims(claims) // validate the data stored inside the token
        } yield claims

        private val splitToken = (jwt: String) => jwt match {
          case jwtRegex(header, body, sig) => ZIO.succeed((header, body, sig))
          case _ => ZIO.fail(new Exception("Token does not match the correct pattern"))
        }

        private val decodeElements = (data: Task[(String, String, String)]) => data map {
          case (header, body, sig) =>
            (JwtBase64.decodeString(header), JwtBase64.decodeString(body), sig)
        }

        private val getJwk = (token: String) =>
          (splitToken andThen decodeElements) (token) flatMap {
            case (header, _, _) =>
              val jwtHeader = JwtCirce.parseHeader(header)
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

        private val validateClaims = (claims: JwtClaim) => withJavaClock.flatMap { implicit clock =>
          if (auth0Conf.audience.map(claims.isValid(s"https://${auth0Conf.domain}/", _)).getOrElse(claims.isValid(s"https://${auth0Conf.domain}/"))) {
            ZIO.succeed(claims)
          } else {
            ZIO.fail(new Exception("The JWT did not pass validation"))
          }
        }
      }
        service
    }

    def auth(token: String): ZIO[AuthService, Throwable, User] = ZIO.accessM(_.get.auth(token))
  }

}