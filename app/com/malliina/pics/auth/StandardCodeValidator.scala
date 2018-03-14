package com.malliina.pics.auth

import com.malliina.http.AsyncHttp
import com.malliina.pics.auth.CodeValidator._
import com.malliina.play.json.JsonMessages
import com.malliina.play.models.Email
import controllers.CognitoControl
import controllers.Social.Conf
import play.api.Logger
import play.api.mvc.Results.{BadGateway, Redirect}
import play.api.mvc.{Call, RequestHeader, Result}

import scala.concurrent.Future

case class CodeValidationConf(redirCall: Call,
                              conf: Conf,
                              client: KeyClient,
                              extraStartParams: Map[String, String] = Map.empty,
                              extraValidateParams: Map[String, String] = Map.empty)

object CodeValidationConf {
  def google(redirCall: Call, conf: Conf, http: AsyncHttp) = CodeValidationConf(
    redirCall,
    conf,
    KeyClient.google(conf.clientId, http),
    Map(LoginHint -> "sub")
  )

  def microsoft(redirCall: Call, conf: Conf, http: AsyncHttp) = CodeValidationConf(
    redirCall,
    conf,
    KeyClient.microsoft(conf.clientId, http),
    extraStartParams = Map("response_mode" -> "query"),
    extraValidateParams = Map(Scope -> scope)
  )
}

object StandardCodeValidator {
  def apply(conf: CodeValidationConf) = new StandardCodeValidator(conf)
}

class StandardCodeValidator(codeConf: CodeValidationConf)
  extends CodeValidator with StateValidation {
  private val log = Logger(getClass)
  val conf = codeConf.conf
  val redirCall = codeConf.redirCall
  val client = codeConf.client
  val http = client.http

  /** The initial result that initiates sign-in.
    */
  override def start(req: RequestHeader): Future[Result] = fetchConf().mapR { oauthConf =>
    val state = CognitoControl.randomState()
    val nonce = CognitoControl.randomState()
    val params = Map(
      ClientId -> conf.clientId,
      ResponseType -> CodeKey,
      RedirectUri -> redirUrl(redirCall, req).url,
      Scope -> urlEncode(scope),
      Nonce -> nonce,
      State -> state
    ) ++ codeConf.extraStartParams
    val url = oauthConf.authorizationEndpoint.append(s"?${stringify(params)}")
    Redirect(url.url).withSession(State -> state, Nonce -> nonce)
  }.onFail { err =>
    log.error(err.message)
    BadGateway(JsonMessages.failure(err.message))
  }

  override def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]] = {
    val params = Map(
      ClientId -> conf.clientId,
      ClientSecret -> conf.clientSecret,
      CodeKey -> code.code,
      RedirectUri -> redirUrl(redirCall, req).url,
      GrantType -> AuthorizationCode
    ) ++ codeConf.extraValidateParams
    fetchConf().flatMapRight { oauthConf =>
      http.postForm(oauthConf.tokenEndpoint.url, params).flatMap { res =>
        res.parse[SimpleTokens].fold(err => fut(Left(JsonError(err))), tokens => {
          client.validate(tokens.idToken).map { result =>
            for {
              verified <- result
              _ <- checkNonce(tokens.idToken, verified, req)
              email <- verified.parsed.readString(EmailKey).map(Email.apply)
            } yield email
          }
        })
      }
    }
  }

  def checkNonce(idToken: IdToken, verified: Verified, req: RequestHeader) =
    verified.parsed.readString(Nonce).flatMap { n =>
      if (req.session.get(Nonce).contains(n)) Right(verified)
      else Left(InvalidClaims(idToken, "Nonce mismatch."))
    }

  def fetchConf(): Future[Either[JsonError, AuthEndpoints]] =
    http.get(client.knownUrl.url).map { res =>
      res.parse[AuthEndpoints].asEither.left.map(err => JsonError(err))
    }
}
