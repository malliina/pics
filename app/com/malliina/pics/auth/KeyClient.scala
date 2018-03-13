package com.malliina.pics.auth

import java.time.Instant

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.http.{AsyncHttp, FullUrl}
import play.api.libs.json.{JsError, Reads}

import scala.concurrent.Future

object KeyClient {
  // https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-v2-tokens
  val knownUrlMicrosoft =
    FullUrl("https", "login.microsoftonline.com", "/common/v2.0/.well-known/openid-configuration")

  def microsoft(clientId: String): KeyClient =
    new KeyClient(knownUrlMicrosoft, MicrosoftValidator(clientId))
}

class KeyClient(knownUrl: FullUrl, validator: TokenValidator) {
  val httpClient = new AsyncHttp()

  def validate(token: TokenValue): Future[Either[JWTError, Verified]] =
    fetchKeys.map { keys =>
      validator.validate(token, keys, Instant.now)
    }

  def fetchKeys: Future[Seq[KeyConf]] = for {
    conf <- fetchConf
    ks <- fetchJson[JWTKeys](conf.jwksUri)
  } yield ks.keys

  def fetchConf: Future[OpenIdConf] = fetchJson[SimpleOpenIdConf](knownUrl)

  def fetchJson[T: Reads](url: FullUrl): Future[T] =
    httpClient.get(url.url).flatMap { res =>
      res.parse[T].fold(
        err => Future.failed(new JsonException(JsError(err))),
        t => Future.successful(t)
      )
    }
}

class JsonException(val error: JsError) extends Exception
