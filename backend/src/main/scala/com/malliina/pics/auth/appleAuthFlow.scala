package com.malliina.pics.auth

import java.time.Instant
import cats.effect.Sync
import cats.syntax.all.*
import com.malliina.http.{FullUrl, HttpClient}
import com.malliina.oauth.TokenResponse
import com.malliina.pics.auth.AppleAuthFlow.staticConf
import com.malliina.values.{Email, ErrorMessage}
import com.malliina.web.OAuthKeys.*
import com.malliina.web.*
import org.http4s.UrlForm

case class AppleResponse(code: Code, state: String)

object AppleResponse:
  def apply(form: UrlForm): Either[ErrorMessage, AppleResponse] =
    def read(key: String) = form.getFirst(key).toRight(ErrorMessage(s"Not found: '$key' in $form."))
    for
      code <- read(CodeKey).map(Code.apply)
      state <- read(State)
    yield AppleResponse(code, state)

object AppleTokenValidator:
  val appleIssuer = Issuer("https://appleid.apple.com")

  def apply(clientIds: Seq[ClientId]) = new AppleTokenValidator(clientIds, Seq(appleIssuer))

class AppleTokenValidator(clientIds: Seq[ClientId], issuers: Seq[Issuer])
  extends TokenValidator(issuers):
  override protected def validateClaims(
    parsed: ParsedJWT,
    now: Instant
  ): Either[JWTError, ParsedJWT] =
    checkContains(Aud, clientIds.map(_.value), parsed).map { _ =>
      parsed
    }

object AppleAuthFlow:
  val emailScope = "email"
  val host = FullUrl.host("appleid.apple.com")

  val authUrl = host / "/auth/authorize"
  val jwksUri = host / "/auth/keys"
  val tokensUrl = host / "/auth/token"

  def staticConf(conf: AuthConf): StaticConf = StaticConf(emailScope, authUrl, tokensUrl, conf)

/** @see
  *   https://developer.apple.com/documentation/signinwithapplejs/incorporating_sign_in_with_apple_into_other_platforms
  */
class AppleAuthFlow[F[_]: Sync](
  authConf: AuthConf,
  validator: AppleTokenValidator,
  http: HttpClient[F]
) extends StaticFlowStart[F]
  with CallbackValidator[F, Email]:
  override val conf: StaticConf = staticConf(authConf)

  override def validate(
    code: Code,
    redirectUrl: FullUrl,
    requestNonce: Option[String]
  ): F[Either[AuthError, Email]] =
    val params = tokenParameters(code, redirectUrl)
    http.postFormAs[TokenResponse](conf.tokenEndpoint, params).flatMap { tokens =>
      http.getAs[JWTKeys](AppleAuthFlow.jwksUri).map { keys =>
        validator.validate(tokens.id_token, keys.keys, Instant.now()).flatMap { v =>
          v.readString(EmailKey).map(Email.apply)
        }
      }
    }

  override def extraRedirParams(redirectUrl: FullUrl): Map[String, String] =
    Map(ResponseType -> CodeKey, "response_mode" -> "form_post")

  def tokenParameters(code: Code, redirUrl: FullUrl) = Map(
    ClientIdKey -> authConf.clientId.value,
    ClientSecretKey -> authConf.clientSecret.value,
    GrantType -> AuthorizationCode,
    CodeKey -> code.code,
    RedirectUri -> redirUrl.url
  )
