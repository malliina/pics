package com.malliina.pics.auth

import com.malliina.play.auth.{AuthError, InvalidSignature, JsonError}
import com.malliina.values.IdToken
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import play.api.libs.json.{JsError, Json, OWrites, Reads}

import scala.util.Try

/**
  * @see https://connect2id.com/products/nimbus-jose-jwt/examples/jws-with-hmac
  * @see https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-hmac
  */
object JWS {
  def apply(secret: String): JWS = new JWS(secret)
}

class JWS(secret: String) {
  def sign(payload: String): IdToken = {
    val signer = new MACSigner(secret)
    val data = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(payload))
    data.sign(signer)
    IdToken(data.serialize())
  }

  def verify(token: IdToken): Either[InvalidSignature, String] = {
    val parsed = JWSObject.parse(token.value)
    val verifier = new MACVerifier(secret)
    val isSignatureOk = parsed.verify(verifier)
    if (!isSignatureOk) Left(InvalidSignature(token))
    else Right(parsed.getPayload.toString)
  }
}

object JsonJWS {
  def apply(secret: String): JsonJWS = new JsonJWS(JWS(secret))
}

class JsonJWS(jws: JWS) {
  def sign[T: OWrites](payload: T): IdToken = jws.sign(Json.stringify(Json.toJson(payload)))

  def verify[T: Reads](token: IdToken): Either[AuthError, T] =
    jws.verify(token).flatMap { str =>
      Try(Json.parse(str)).toEither.left
        .map { _ =>
          JsonError(s"Not JSON: '$str'.")
        }
        .flatMap { json =>
          json.validate[T].asEither.left.map { errors =>
            JsonError(JsError(errors))
          }
        }
    }
}
