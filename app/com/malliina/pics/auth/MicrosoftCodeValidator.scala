package com.malliina.pics.auth

import com.malliina.http.AsyncHttp
import com.malliina.play.json.JsonMessages
import com.malliina.play.models.Email
import controllers.CognitoControl
import controllers.Social.Conf
import play.api.Logger
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, RequestHeader, Result, Results}

import scala.concurrent.Future

class MicrosoftCodeValidator(redirCall: Call, conf: Conf, http: AsyncHttp)
  extends CodeValidator with StateValidation {

  private val log = Logger(getClass)

  val discoverUrl = "https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration"

  val microsoftClient = KeyClient.microsoft(conf.clientId)
  val scope = "openid email"

  def start(req: RequestHeader): Future[Result] = startMicrosoft(isImplicit = false, req)

  /** https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-v2-protocols-oauth-code
    *
    * @param isImplicit true for implicit flow, false for code grant flow
    */
  private def startMicrosoft(isImplicit: Boolean, req: RequestHeader): Future[Result] = {
    val responseType = if (isImplicit) "id_token" else "code"
    val responseMode = if (isImplicit) "form_post" else "query"
    fetchConf().mapR { oauthConf =>
      val state = CognitoControl.randomState()
      val nonce = CognitoControl.randomState()
      val params = Map(
        ClientId -> conf.clientId,
        "response_type" -> responseType,
        "response_mode" -> responseMode,
        RedirectUri -> urlEncode(redirUrl(redirCall, req).url),
        Scope -> urlEncode(scope),
        Nonce -> nonce,
        State -> state
      )
      val url = oauthConf.authorizationEndpoint.append(s"?${stringify(params)}")
      Redirect(url.url).withSession(State -> state, Nonce -> nonce)
    }.onFail { err =>
      log.error(err.message)
      Results.BadGateway(JsonMessages.failure(err.message))
    }
  }

  def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]] = {
    val params = Map(
      ClientId -> conf.clientId,
      Scope -> scope,
      CodeKey -> code.code,
      RedirectUri -> redirUrl(redirCall, req).url,
      "grant_type" -> "authorization_code",
      ClientSecret -> conf.clientSecret
    )
    fetchConf().flatMapRight { oauthConf =>
      http.postForm(oauthConf.tokenEndpoint.url, params).flatMap { res =>
        res.parse[MicrosoftTokens].fold(
          err => {
            fut(Left(JsonError(err)))
          },
          tokens => {
            microsoftClient.validate(tokens.idToken).map { result =>
              def checkNonce(v: Verified) = {
                v.parsed.readString(Nonce).flatMap { n =>
                  if (req.session.get(Nonce).contains(n)) Right(v)
                  else Left(InvalidClaims(tokens.idToken, "Nonce mismatch."))
                }
              }

              for {
                verified <- result
                _ <- checkNonce(verified)
                email <- verified.parsed.readString(EmailKey).map(Email.apply)
              } yield email
            }
          }
        )
      }
    }
  }

  def fetchConf(): Future[Either[JsonError, MicrosoftOAuthConf]] =
    http.get(discoverUrl).map { res =>
      res.parse[MicrosoftOAuthConf].asEither.left.map(err => JsonError(err))
    }
}
