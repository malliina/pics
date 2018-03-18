package com.malliina.play.auth

import com.malliina.concurrent.ExecutionContexts
import com.malliina.http.{AsyncHttp, FullUrl, WebResponse}
import com.malliina.play.auth.CodeValidator._
import com.malliina.play.http.FullUrls
import com.malliina.play.models.Email
import controllers.CognitoControl
import play.api.Logger
import play.api.libs.json.Reads
import play.api.mvc.{Call, RequestHeader, Result}

import scala.concurrent.Future

object CodeValidator {
  private val log = Logger(getClass)

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
    response.map(r => r.parse[T].asEither.left.map(err => JsonError(err)))
}

trait CodeValidator extends AuthValidator {
  def http: AsyncHttp

  def conf: AuthConf

  def redirCall: Call

  def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]]

  override def validateCallback(req: RequestHeader): Future[Result] = {
    val requestState = req.getQueryString(State)
    val sessionState = req.session.get(State)
    val isStateOk = requestState.exists(rs => sessionState.contains(rs))
    if (isStateOk) {
      req.getQueryString(CodeKey).map { code =>
        validate(Code(code), req).map(toResult)
      }.getOrElse {
        log.error(s"Authentication failed, code mismatch. $req")
        unauthorizedFut
      }
    } else {
      log.error(s"Authentication failed, state mismatch. $req")
      unauthorizedFut
    }
  }

  protected def urlEncode(s: String) = AuthValidator.urlEncode(s)

  protected def randomState() = CognitoControl.randomState()

  protected def redirUrl(call: Call, rh: RequestHeader) = urlEncode(FullUrls(call, rh).url)

  /** Not encoded.
    */
  protected def validationParams(code: Code, req: RequestHeader) = {
    Map(
      ClientId -> conf.clientId,
      ClientSecret -> conf.clientSecret,
      RedirectUri -> FullUrls(redirCall, req).url,
      CodeKey -> code.code
    )
  }

  def postForm[T: Reads](url: FullUrl, params: Map[String, String]) =
    readAs[T](http.postForm(url.url, params))

  def postEmpty[T: Reads](url: FullUrl,
                          headers: Map[String, String] = Map.empty,
                          params: Map[String, String] = Map.empty) =
    readAs[T](http.postEmpty(url.url, headers, params))

  def getJson[T: Reads](url: FullUrl) = readAs[T](http.get(url.url))
}
