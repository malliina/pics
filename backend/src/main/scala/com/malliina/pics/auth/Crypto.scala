package com.malliina.pics.auth

import java.nio.charset.StandardCharsets

import com.malliina.pics.Errors
import com.malliina.pics.auth.Crypto.PrivateKey
import com.malliina.web.Utils
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Hex

object Crypto:
  case class PrivateKey(key: Array[Byte]) extends AnyVal

  def apply(key: PrivateKey): Crypto = new Crypto(key)

/** @see
  *   https://github.com/reactormonk/cryptobits/blob/master/cryptobits/src/cryptobits.scala
  */
class Crypto(key: PrivateKey):
  val algorithm = "HmacSHA1"
  val sep = "-"

  def randomString() = Utils.randomString()

  def sign(payload: String): String =
    val nonce = randomString()
    val joined = s"$nonce$sep$payload"
    val signature = hexSignature(joined)
    s"$signature$sep$joined"

  def verify(token: String): Either[Errors, String] =
    token.split(sep, 3) match
      case Array(signature, nonce, payload) =>
        val signed = hexSignature(s"$nonce$sep$payload")
        if constantTimeEquals(signature, signed) then Right(payload)
        else Left(Errors.single(s"Signature mismatch. Token: '$token'."))
      case _ =>
        Left(Errors.single(s"Invalid token format: '$token'."))

  private def hexSignature(message: String): String =
    val mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key.key, algorithm))
    Hex.encodeHexString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)))

  private def constantTimeEquals(a: String, b: String): Boolean =
    var equal = 0
    for i <- 0 until (a.length min b.length) do equal |= a(i) ^ b(i)
    if a.length != b.length then false
    else equal == 0
