package com.malliina.pics.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.malliina.concurrent.ExecutionContexts
import com.malliina.pics.auth.CodeValidator._
import com.malliina.play.http.FullUrls
import com.malliina.play.json.JsonMessages
import com.malliina.play.models.Email
import controllers.routes
import play.api.Logger
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, RequestHeader, Result, Results}

import scala.concurrent.Future

trait NoStateValidation {
  self: CodeValidator =>

  override def validateCode(req: RequestHeader): Future[Result] =
    self.onStateOk(req)
}

trait StateValidation {
  self: CodeValidator =>

  override def validateCode(req: RequestHeader): Future[Result] = {
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
}

trait CodeValidator {
  implicit val ec = ExecutionContexts.cached

  /** The initial result that initiates sign-in.
    */
  def start(req: RequestHeader): Future[Result]

  /** The callback in the auth flow, i.e. the result for redirect URIs.
    */
  def validateCode(req: RequestHeader): Future[Result]

  def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]]

  def onStateOk(req: RequestHeader): Future[Result] = {
    req.getQueryString(CodeKey).map { code =>
      validate(Code(code), req).map(toResult)
    }.getOrElse {
      log.error(s"Authentication failed, code mismatch. $req")
      unauthorizedFut
    }
  }

  def toResult(e: Either[AuthError, Email]): Result =
    e.fold(
      err => {
        log.error(err.message)
        unauthorized
      },
      email => {
        log.info(s"Logging '$email' in through OAuth code flow.")
        Redirect(routes.PicsController.list()).withSession("username" -> email.email)
      }
    )

  def unauthorizedFut = fut(unauthorized)

  def unauthorized = Results.Unauthorized(JsonMessages.failure("Authentication failed."))

  protected def urlEncode(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.name())

  protected def stringify(map: Map[String, String]) =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  protected def fut[T](t: T) = Future.successful(t)

  protected def redirUrl(call: Call, rh: RequestHeader) = FullUrls(call, rh)
}
