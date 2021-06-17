package io.conduktor.api.auth

import com.auth0.jwk.Jwk
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.conduktor.api.config.Auth0Config
import io.conduktor.api.types.UserName
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import zio.{Has, URLayer, ZIO, _}

final case class User(name: UserName)
object User {
  implicit val userCodec: Codec[User] = deriveCodec
}

trait AuthService {
  def auth(token: String): Task[User]
}

object AuthService {
  def auth(token: String): ZIO[Has[AuthService], Throwable, User] = ZIO.accessM(_.get.auth(token))

  private[auth] def jwk(auth0Config: Auth0Config): Task[Jwk] = Task(auth0Config.jwkProvider.get(null))
}

final class JwtAuthService(jwk: Jwk, auth0Config: Auth0Config, clock: java.time.Clock) extends AuthService {

  private val algorithms = List(JwtAlgorithm.RS256)

  /**
   * Validate the data stored inside the token
   */
  private def validateClaims(claims: JwtClaim): ZIO[Any, Throwable, Unit] =
    ZIO
      .fail(new RuntimeException("The JWT did not pass validation"))
      .unless(claims.isValid(auth0Config.issuer)(clock))

  override def auth(token: String): Task[User] =
    for {
      bearer <- ZIO.fromOption {
                  token match {
                    case s"Bearer $a" => Some(a)
                    case _            => None
                  }
                }.orElseFail(new RuntimeException("Invalid auth header"))
      claims <- ZIO.fromTry(JwtCirce.decode(bearer, jwk.getPublicKey, algorithms))
      _      <- validateClaims(claims)
      user   <- ZIO.fromEither(decode[User](claims.content))
    } yield user

}

object JwtAuthService {

  /**
   * I didn't find how to combine these two layers.
   *
   * Ideally, I'd prefer to expose only `layer` 🤷‍♂️
   */
  val jwkLayer: ZLayer[Has[Auth0Config], Throwable, Has[Jwk]] =
    ZIO.accessM[Has[Auth0Config]](c => AuthService.jwk(c.get)).toLayer

  val layer: URLayer[Has[Jwk] with Has[Auth0Config] with Has[java.time.Clock], Has[AuthService]] =
    (new JwtAuthService(_, _, _)).toLayer

}
