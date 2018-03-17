package com.malliina.play.auth

import java.time.Instant

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.http.{AsyncHttp, FullUrl}
import play.api.libs.json.Reads

import scala.concurrent.Future

object KeyClient {
  // https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-v2-tokens
  val knownUrlMicrosoft =
    FullUrl("https", "login.microsoftonline.com", "/common/v2.0/.well-known/openid-configuration")

  val knownUrlGoogle =
    FullUrl("https", "accounts.google.com", "/.well-known/openid-configuration")

  def microsoft(clientId: String, http: AsyncHttp): KeyClient =
    new KeyClient(knownUrlMicrosoft, MicrosoftValidator(clientId), http)

  def google(clientId: String, http: AsyncHttp): KeyClient =
    new KeyClient(knownUrlGoogle, GoogleValidator(clientId), http)
}

class KeyClient(val knownUrl: FullUrl, validator: TokenValidator, val http: AsyncHttp) {
  def validate(token: TokenValue): Future[Either[AuthError, Verified]] =
    fetchKeys().mapRight { keys =>
      validator.validate(token, keys, Instant.now)
    }

  def fetchKeys(): Future[Either[JsonError, Seq[KeyConf]]] =
    fetchConf().flatMapRight { conf =>
      fetchJson[JWTKeys](conf.jwksUri).mapR(_.keys)
    }

  def fetchConf() = fetchJson[AuthEndpoints](knownUrl)

  def fetchJson[T: Reads](url: FullUrl): Future[Either[JsonError, T]] =
    http.get(url.url).map { res =>
      res.parse[T].asEither.left.map(err => JsonError(err))
    }
}
