package com.malliina.pics.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.malliina.concurrent.ExecutionContexts
import com.malliina.http.WebResponse
import com.malliina.pics.auth.AuthValidator.log
import com.malliina.play.http.FullUrls
import com.malliina.play.json.JsonMessages
import com.malliina.play.models.Email
import controllers.routes
import play.api.Logger
import play.api.libs.json.Reads
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, RequestHeader, Result, Results}

import scala.concurrent.Future

object AuthValidator {
  private val log = Logger(getClass)

  def urlEncode(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
}

trait AuthValidator {
  implicit val ec = ExecutionContexts.cached

  /** The initial result that initiates sign-in.
    */
  def start(req: RequestHeader): Future[Result]

  /** The callback in the auth flow, i.e. the result for redirect URIs.
    */
  def validateCallback(req: RequestHeader): Future[Result]

  protected def toResult(e: Either[AuthError, Email]): Result =
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

  protected def stringify(map: Map[String, String]) =
    map.map { case (key, value) => s"$key=$value" }.mkString("&")

  protected def unauthorizedFut = fut(unauthorized)

  protected def unauthorized = Results.Unauthorized(JsonMessages.failure("Authentication failed."))

  protected def fut[T](t: T) = Future.successful(t)

  protected def redirUrl(call: Call, rh: RequestHeader) = FullUrls(call, rh)

  protected def readAs[T: Reads](response: Future[WebResponse]) = CodeValidator.readAs[T](response)
}
