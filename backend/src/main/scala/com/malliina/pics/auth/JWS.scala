package com.malliina.pics.auth

import com.malliina.web.{AuthError, InvalidSignature, JsonError}
import com.malliina.values.{IdToken, TokenValue}
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import io.circe.*
import io.circe.syntax.EncoderOps
import io.circe.parser.parse

/** @see
  *   https://connect2id.com/products/nimbus-jose-jwt/examples/jws-with-hmac
  * @see
  *   https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-hmac
  */
class JWS(secret: SecretKey):
  def sign(payload: String): IdToken =
    val signer = new MACSigner(secret.value)
    val data = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(payload))
    data.sign(signer)
    IdToken(data.serialize())

  def verify(token: TokenValue): Either[InvalidSignature, String] =
    val parsed = JWSObject.parse(token.value)
    val verifier = new MACVerifier(secret.value)
    val isSignatureOk = parsed.verify(verifier)
    if !isSignatureOk then Left(InvalidSignature(token))
    else Right(parsed.getPayload.toString)

class JsonJWS(jws: JWS):
  def sign[T: Encoder](payload: T): IdToken = jws.sign(payload.asJson.noSpaces)

  def verify[T: Decoder](token: IdToken): Either[AuthError, T] =
    jws
      .verify(token)
      .flatMap: str =>
        parse(str).left
          .map: _ =>
            JsonError(s"Not JSON: '$str'.")
          .flatMap: json =>
            json
              .as[T]
              .left
              .map: errors =>
                JsonError(errors.message)
