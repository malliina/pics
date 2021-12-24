package com.malliina.pics.auth

import com.malliina.config.ConfigReadable
import com.malliina.pics.PicsConf.ConfigOps
import com.malliina.pics.auth.AppleTokenValidator.appleIssuer
import com.malliina.pics.auth.SignInWithApple.Conf
import com.malliina.web.{ClientId, ClientSecret}
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}

import java.nio.file.{Files, Path}
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Base64, Date}
import scala.jdk.CollectionConverters.ListHasAsScala

object SignInWithApple:
  case class Conf(privateKey: Path, keyId: String, teamId: String, clientId: ClientId)

  object Conf:
    implicit val reader: ConfigReadable[Conf] = ConfigReadable.config.emap { obj =>
      for
        keyPath <- obj.read[Path]("privateKey")
        keyId <- obj.read[String]("keyId")
        team <- obj.read[String]("teamId")
        clientId <- obj.read[ClientId]("clientId")
      yield Conf(keyPath, keyId, team, clientId)
    }

/** https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens
  */
class SignInWithApple(conf: Conf):
  private val content = Files.readAllLines(conf.privateKey).asScala.drop(1).dropRight(1).mkString
  private val decoded = Base64.getDecoder.decode(content)
  private val keySpec = new PKCS8EncodedKeySpec(decoded)
  private val keyFactory = KeyFactory.getInstance("EC")
  private val key = keyFactory.generatePrivate(keySpec).asInstanceOf[ECPrivateKey]
  private val signer = new ECDSASigner(key)
  private val header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(conf.keyId).build()

  def signInWithAppleToken(now: Instant): ClientSecret =
    val issuedAt = Date.from(now)
    val exp = Date.from(now.plus(179, ChronoUnit.DAYS))
    val claims = new JWTClaimsSet.Builder()
      .issuer(conf.teamId)
      .issueTime(issuedAt)
      .expirationTime(exp)
      .audience(appleIssuer.value)
      .subject(conf.clientId.value)
      .build()
    val signable = new SignedJWT(header, claims)
    signable.sign(signer)
    ClientSecret(signable.serialize())
