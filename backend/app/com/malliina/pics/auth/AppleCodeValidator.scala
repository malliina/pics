package com.malliina.pics.auth

import java.time.Instant

import com.malliina.http.FullUrl
import com.malliina.oauth.TokenResponse
import com.malliina.play.auth.StaticCodeValidator.StaticConf
import com.malliina.play.auth._
import com.malliina.values.{Email, ErrorMessage}
import play.api.mvc.{RequestHeader, Result}
import com.malliina.play.auth.OAuthKeys.{
  ClientId => ClientIdKey,
  ClientSecret => ClientSecretKey,
  _
}
import com.malliina.play.http.FullUrls

import scala.concurrent.Future

case class AppleResponse(code: Code, state: String)

object AppleResponse {
  def apply(form: Map[String, Seq[String]]): Either[ErrorMessage, AppleResponse] = {
    def read(key: String) =
      form.get(key).flatMap(_.headOption).toRight(ErrorMessage(s"Not found: '$key' in $form."))
    for {
      code <- read(CodeKey).map(Code.apply)
      state <- read(State)
    } yield AppleResponse(code, state)
  }
}

object AppleTokenValidator {
  def apply(clientIds: Seq[ClientId]) = new AppleTokenValidator(clientIds, Seq(Issuer.apple))
}

class AppleTokenValidator(clientIds: Seq[ClientId], issuers: Seq[Issuer])
  extends TokenValidator(issuers.map(_.value)) {
  override protected def validateClaims(
    parsed: ParsedJWT,
    now: Instant
  ): Either[JWTError, ParsedJWT] = checkContains(Aud, clientIds.map(_.value), parsed).map { _ =>
    parsed
  }

}

object AppleCodeValidator {
  val emailScope = "email"
  val host = FullUrl.host("appleid.apple.com")

  val authUrl = host / "/auth/authorize"
  val jwksUri = host / "/auth/keys"
  val tokensUrl = host / "/auth/token"

  def apply(oauth: OAuthConf[Email], validator: AppleTokenValidator): AppleCodeValidator =
    new AppleCodeValidator(oauth, validator)

  def staticConf(conf: AuthConf) = StaticConf(emailScope, authUrl, tokensUrl, conf)
}

/** @see https://developer.apple.com/documentation/signinwithapplejs/incorporating_sign_in_with_apple_into_other_platforms
  */
class AppleCodeValidator(val oauth: OAuthConf[Email], validator: AppleTokenValidator)
  extends StaticCodeValidator[Email, Email]("Apple", AppleCodeValidator.staticConf(oauth.conf)) {

  override def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]] = {
    val params = tokenParameters(code, FullUrls(redirCall, req))
    postForm[TokenResponse](staticConf.tokenEndpoint, params).flatMap { tokens =>
      http.getAs[JWTKeys](AppleCodeValidator.jwksUri).map { keys =>
        validator.validate(IdToken(tokens.id_token.token), keys.keys, Instant.now()).flatMap { v =>
          v.readString(EmailKey).map(Email.apply)
        }
      }
    }
  }

  override def onOutcome(outcome: Either[AuthError, Email], req: RequestHeader): Result =
    handler.resultFor(outcome, req)

  override def extraRedirParams(rh: RequestHeader): Map[String, String] =
    Map(ResponseType -> CodeKey, "response_mode" -> "form_post")

  def tokenParameters(code: Code, redirUrl: FullUrl) = Map(
    ClientIdKey -> clientConf.clientId,
    ClientSecretKey -> clientConf.clientSecret,
    GrantType -> AuthorizationCode,
    CodeKey -> code.code,
    RedirectUri -> redirUrl.url
  )
}
