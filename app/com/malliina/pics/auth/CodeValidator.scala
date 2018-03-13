package com.malliina.pics.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.malliina.pics.auth.CodeValidator.log
import com.malliina.play.http.FullUrls
import com.malliina.play.json.JsonMessages
import com.malliina.play.models.Email
import controllers.routes
import play.api.Logger
import play.api.libs.json.JsError
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, RequestHeader, Result, Results}

import scala.concurrent.Future

object CodeValidator {
  implicit val log = Logger(getClass)
}

trait NoStateValidation {
  self: CodeValidator =>

  def validate(req: RequestHeader): Future[Result] =
    self.onStateOk(req)
}

trait StateValidation {
  self: CodeValidator =>

  def validate(req: RequestHeader): Future[Result] = {
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

trait CodeValidator {
  implicit val ec = com.malliina.concurrent.ExecutionContexts.cached

  val ClientId = "client_id"
  val ClientSecret = "client_secret"
  val CodeKey = "code"
  val EmailKey = "email"
  val IdTokenKey = "id_token"
  val Nonce = "nonce"
  val RedirectUri = "redirect_uri"
  val Scope = "scope"
  val State = "state"

  def start(req: RequestHeader): Future[Result]

  def validate(req: RequestHeader): Future[Result]

  def onStateOk(req: RequestHeader): Future[Result] = {
    req.getQueryString(CodeKey).map { code =>
      validate(Code(code), req).map(toResult)
    }.getOrElse {
      log.error(s"Authentication failed, code mismatch. $req")
      unauthorizedFut
    }
  }

  def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]]

  def toResult(e: Either[AuthError, Email]) =
    e.fold(
      err => {
        log.error(err.message)
        unauthorized
      },
      email => {
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

  def fail(err: JsError) = Future.failed(new JsonException(err))
}

case class Code(code: String)
