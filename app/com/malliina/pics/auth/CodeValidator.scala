package com.malliina.pics.auth

import com.malliina.concurrent.ExecutionContexts
import com.malliina.http.WebResponse
import com.malliina.pics.auth.CodeValidator._
import com.malliina.play.models.Email
import play.api.Logger
import play.api.libs.json.Reads
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

trait NoStateValidation {
  self: CodeValidator =>

  override def validateCallback(req: RequestHeader): Future[Result] =
    self.onStateOk(req)
}

trait StateValidation {
  self: CodeValidator =>

  override def validateCallback(req: RequestHeader): Future[Result] = {
    val requestState = req.getQueryString(State)
    val sessionState = req.session.get(State)
    val isStateOk = requestState.exists(rs => sessionState.contains(rs))
    if (isStateOk) {
      self.onStateOk(req)
    } else {
      log.error(s"Authentication failed, state mismatch. $req")
      unauthorizedFut
    }
  }
}

object CodeValidator {
  implicit val log = Logger(getClass)

  val AuthorizationCode = "authorization_code"
  val ClientId = "client_id"
  val ClientSecret = "client_secret"
  val CodeKey = "code"
  val EmailKey = "email"
  val GrantType = "grant_type"
  val IdTokenKey = "id_token"
  val LoginHint = "login_hint"
  val Nonce = "nonce"
  val RedirectUri = "redirect_uri"
  val ResponseType = "response_type"
  val Scope = "scope"
  val State = "state"

  val scope = "openid email"
  implicit val ec = ExecutionContexts.cached

  def readAs[T: Reads](response: Future[WebResponse]) =
    response.map(_.parse[T].asEither.left.map(err => JsonError(err)))
}

trait CodeValidator extends AuthValidator {
  def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]]

  def onStateOk(req: RequestHeader): Future[Result] = {
    req.getQueryString(CodeKey).map { code =>
      validate(Code(code), req).map(toResult)
    }.getOrElse {
      log.error(s"Authentication failed, code mismatch. $req")
      unauthorizedFut
    }
  }

  protected def urlEncode(s: String) = AuthValidator.urlEncode(s)
}
