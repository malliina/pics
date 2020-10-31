package com.malliina.pics.auth

import com.malliina.concurrent.Execution.cached
import com.malliina.pics.{Errors, PicRequest, SingleError}
import com.malliina.play.auth.{AuthError, JWTUser, MissingCredentials, Verified}
import com.malliina.play.controllers.AuthBundle
import com.malliina.values.{AccessToken, Email}
import controllers.{JWTAuth, PicsController, Social}
import org.http4s.MediaType
import org.http4s.util.CaseInsensitiveString
import play.api.Logger
import play.api.http.{HeaderNames, MediaRange}
import play.api.mvc.{RequestHeader, Result, Results}

import scala.concurrent.Future

case class CognitoSocialUser(email: Email, verified: Verified)

trait PicsAuthLike {
  def authenticate(rh: RequestHeader): Future[Either[Result, PicRequest]]
  def validateToken(token: AccessToken): Future[Either[AuthError, JWTUser]]
  def unauth = Results.Unauthorized(Errors.single("Unauthorized."))
}

object PicsAuthenticator {
  private val log = Logger(getClass)

  def apply(jwtAuth: JWTAuth, bundle: AuthBundle[PicRequest]) =
    new PicsAuthenticator(jwtAuth, bundle)
}

class PicsAuthenticator(jwtAuth: JWTAuth, bundle: AuthBundle[PicRequest]) extends PicsAuthLike {
  val pics10 = new MediaType("application", "vnd.pics.v10+json")
  val html = MediaType.text.html
  val json = MediaType.application.json

  def authenticate2(headers: org.http4s.Headers) = {
    val mediaRange = headers
      .get(CaseInsensitiveString("Accept"))
      .map { h =>
        org.http4s.MediaRange.parse(h.value)
      }
      .getOrElse { Right(org.http4s.MediaRange.`*/*`) }
    mediaRange.map { range =>
      if (range.satisfies(html)) ???
      else if (range.satisfies(pics10)) ???
      else if (range.satisfies(json)) ???
      else fut(Left(Errors.single(s"No acceptable media range found.")))
    }
  }

  override def authenticate(rh: RequestHeader): Future[Either[Result, PicRequest]] = {
    val execAuth: PartialFunction[MediaRange, Future[Either[Result, PicRequest]]] = {
      case PicsController.HtmlAccept() => authHtml(rh)
      case PicsController.Json10()     => authJwt(rh)
      case PicsController.JsonAccept() => authJwt(rh)
    }

    def acceptedResult(ms: Seq[MediaRange]): Future[Either[Result, PicRequest]] = ms match {
      case Nil =>
        fut(
          Left(
            Results
              .NotAcceptable(Errors.single(s"No acceptable '${HeaderNames.ACCEPT}' header found."))
          )
        )
      case Seq(m, tail @ _*) => execAuth.applyOrElse(m, (_: MediaRange) => acceptedResult(tail))
    }

    val accepted =
      if (rh.acceptedTypes.isEmpty) Seq(new MediaRange("*", "*", Nil, None, Nil))
      else rh.acceptedTypes

    acceptedResult(accepted)
  }

  override def validateToken(token: AccessToken): Future[Either[AuthError, JWTUser]] =
    jwtAuth.validateToken(token)

  def authJwt(rh: RequestHeader) = jwtAuth.userOrAnon(rh).map(convertError)

  def authHtml(rh: RequestHeader): Future[Either[Result, PicRequest]] =
    bundle.authenticator.authenticate(rh).map { e =>
      e.fold(
        {
          case MissingCredentials(req) if !req.cookies.exists(_.name == Social.ProviderCookie) =>
            Right(PicRequest.anon(req))
          case other =>
            Left(bundle.onUnauthorized(other))
        },
        u => Right(u)
      )
    }

  private def fut[T](t: T) = Future.successful(t)

  private def convertError[U](e: Either[AuthError, U]) =
    e.left.map { error =>
      fail(SingleError(error.message.message, error.key))
    }

  private def fail(err: SingleError) = JWTAuth.failSingle(err)
}
