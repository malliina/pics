package com.malliina.pics.auth

import java.text.ParseException
import java.time.Instant

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT

object TokenValidator {
  def read[T](token: AccessToken, f: => T, onMissing: => String): Either[JWTError, T] =
    try {
      Option(f).toRight(MissingData(token, onMissing))
    } catch {
      case pe: ParseException => Left(ParseError(token, pe))
    }
}

abstract class TokenValidator(conf: KeyConf) {

  import TokenValidator.read

  private val publicKey = new RSAKey.Builder(conf.n, conf.e)
    .keyUse(conf.use)
    .keyID(conf.kid)
    .build()
  private val verifier = new RSASSAVerifier(publicKey)

  def validate(token: AccessToken): Either[JWTError, Verified] =
    for {
      parsed <- parse(token)
      verified <- verify(parsed)
    } yield verified

  protected def parse(token: AccessToken): Either[JWTError, ParsedJWT] = for {
    jwt <- read(token, SignedJWT.parse(token.token), "token")
    claims <- read(token, jwt.getJWTClaimsSet, "claims")
    kid <- read(token, jwt.getHeader.getKeyID, "kid")
    iss <- read(token, claims.getIssuer, "iss")
    exp <- read(token, claims.getExpirationTime, "exp")
  } yield ParsedJWT(jwt, claims, kid, iss, exp.toInstant, token)

  def verify(parsed: ParsedJWT): Either[JWTError, Verified] = {
    val now = Instant.now()
    val token = parsed.token
    if (parsed.iss != conf.issuer) Left(IssuerMismatch(token, parsed.iss, conf.issuer))
    else if (parsed.kid != conf.kid) Left(InvalidKeyId(token, parsed.kid, conf.kid))
    else if (!isSignatureValid(parsed.jwt)) Left(InvalidSignature(token))
    else if (!now.isBefore(parsed.exp)) Left(Expired(token, parsed.exp, now))
    else validateClaims(parsed).map(_ => Verified(parsed))
  }

  protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT]

  protected def isSignatureValid(unverified: SignedJWT): Boolean =
    unverified.verify(verifier)
}

object LiberalValidator {
  val auth0 = new LiberalValidator(KeyConf.auth0)
}

class LiberalValidator(conf: KeyConf) extends TokenValidator(conf) {
  override protected def validateClaims(parsed: ParsedJWT): Either[JWTError, ParsedJWT] =
    Right(parsed)
}
