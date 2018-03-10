package com.malliina.pics.auth

import java.text.ParseException
import java.time.Instant

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT

object TokenValidator {
  def read[T](token: TokenValue, f: => T, onMissing: => String): Either[JWTError, T] =
    try {
      Option(f).toRight(MissingData(token, onMissing))
    } catch {
      case pe: ParseException => Left(ParseError(token, pe))
    }
}

/**
  * @param keys public keys used to validate tokens
  * @param issuer issuer
  * @tparam T type of token
  * @tparam U type of user
  */
abstract class TokenValidator[T <: TokenValue, U](keys: Seq[KeyConf], issuer: String) {

  import TokenValidator.read

  private val publicKeys = keys.map { conf =>
    new RSAKey.Builder(conf.n, conf.e)
      .keyUse(conf.use)
      .keyID(conf.kid)
      .build()
  }
  private val verifiers: Map[String, RSASSAVerifier] =
    publicKeys.map { publicKey => publicKey.getKeyID -> new RSASSAVerifier(publicKey) }.toMap

  protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT]

  protected def toUser(v: Verified): Either[JWTError, U]

  def validate(token: T): Either[JWTError, U] =
    for {
      parsed <- parse(token)
      verified <- verify(parsed)
      user <- toUser(verified)
    } yield user

  protected def parse(token: T): Either[JWTError, ParsedJWT] = for {
    jwt <- read(token, SignedJWT.parse(token.token), "token")
    claims <- read(token, jwt.getJWTClaimsSet, "claims")
    kid <- read(token, jwt.getHeader.getKeyID, "kid")
    iss <- read(token, claims.getIssuer, "iss")
    exp <- read(token, claims.getExpirationTime, "exp")
  } yield ParsedJWT(jwt, claims, kid, iss, exp.toInstant, token)

  protected def verify(parsed: ParsedJWT): Either[JWTError, Verified] = {
    val now = Instant.now()
    val token = parsed.token
    if (parsed.iss != issuer) {
      Left(IssuerMismatch(token, parsed.iss, issuer))
    } else {
      verifiers.get(parsed.kid).map { verifier =>
        if (!isSignatureValid(parsed.jwt, verifier)) Left(InvalidSignature(token))
        else if (!now.isBefore(parsed.exp)) Left(Expired(token, parsed.exp, now))
        else validateClaims(parsed).map(p => Verified(p))
      }.getOrElse {
        Left(InvalidKeyId(token, parsed.kid, keys.map(_.kid)))
      }
    }
  }

  protected def isSignatureValid(unverified: SignedJWT, verifier: RSASSAVerifier): Boolean =
    unverified.verify(verifier)
}

object LiberalValidator {
  val auth0 = new LiberalValidator(KeyConf.auth0, "https://malliina.eu.auth0.com/")
}

class LiberalValidator(conf: KeyConf, issuer: String)
  extends TokenValidator[AccessToken, Verified](Seq(conf), issuer) {
  override protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT] =
    Right(parsed)

  override protected def toUser(v: Verified) =
    Right(v)
}
