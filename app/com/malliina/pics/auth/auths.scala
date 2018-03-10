package com.malliina.pics.auth

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.{Errors, PicRequest}
import com.malliina.play.auth.MissingCredentials
import com.malliina.play.controllers.AuthBundle
import controllers.{JWTAuth, PicsController}
import play.api.http.{HeaderNames, MediaRange}
import play.api.mvc.{RequestHeader, Result, Results}

import scala.concurrent.Future

trait PicsAuthLike {
  def authenticate(rh: RequestHeader): Future[Either[Result, PicRequest]]

  def validateToken(token: AccessToken): Either[Result, CognitoUser]

  def unauth = Results.Unauthorized(Errors.single("Unauthorized."))
}

object PicsAuthenticator {
  def apply(jwtAuth: JWTAuth, bundle: AuthBundle[PicRequest]) =
    new PicsAuthenticator(jwtAuth, bundle)
}

class PicsAuthenticator(jwtAuth: JWTAuth, bundle: AuthBundle[PicRequest]) extends PicsAuthLike {
  override def authenticate(rh: RequestHeader): Future[Either[Result, PicRequest]] = {
    val execAuth: PartialFunction[MediaRange, Future[Either[Result, PicRequest]]] = {
      case PicsController.HtmlAccept() => authHtml(rh)
      case PicsController.Json10() => authJwt(rh)
      case PicsController.JsonAccept() => authJwt(rh)
    }

    def acceptedResult(ms: Seq[MediaRange]): Future[Either[Result, PicRequest]] = ms match {
      case Nil => fut(Left(Results.NotAcceptable(Errors.single(s"No acceptable '${HeaderNames.ACCEPT}' header found."))))
      case Seq(m, tail@_*) => execAuth.applyOrElse(m, (_: MediaRange) => acceptedResult(tail))
    }

    val accepted =
      if (rh.acceptedTypes.isEmpty) Seq(new MediaRange("*", "*", Nil, None, Nil))
      else rh.acceptedTypes

    acceptedResult(accepted)
  }

  override def validateToken(token: AccessToken): Either[Result, CognitoUser] =
    jwtAuth.validateToken(token)

  def authJwt(rh: RequestHeader) = fut(jwtAuth.userOrAnon(rh))

  def authHtml(rh: RequestHeader): Future[Either[Result, PicRequest]] =
    bundle.authenticator.authenticate(rh).map { e =>
      e.fold(
        {
          case MissingCredentials(req) => Right(PicRequest.anon(req))
          case other => Left(bundle.onUnauthorized(other))
        },
        u => Right(u)
      )
    }

  private def fut[T](t: T) = Future.successful(t)
}
