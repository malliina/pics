package com.malliina.pics.auth

import akka.stream.Materializer
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.Errors
import com.malliina.play.controllers.AuthBundle
import com.malliina.play.http.AuthedRequest
import controllers.{JWTAuth, PicsController}
import play.api.http.{HeaderNames, MediaRange}
import play.api.libs.streams.Accumulator
import play.api.mvc._

import scala.concurrent.Future

class PicsAuth(jwtAuth: JWTAuth,
               bundle: AuthBundle[AuthedRequest],
               mat: Materializer,
               actions: DefaultActionBuilder) extends ControllerHelpers {

  def authAction(a: AuthedRequest => Future[Result]) =
    authed(req => actions.async(a(req)))

  def authed(a: AuthedRequest => EssentialAction) = EssentialAction { rh =>
    val futAcc = auth(rh).map(_.fold(
      err => actions(err)(rh),
      req => a(req)(rh)
    ))
    Accumulator.flatten(futAcc)(mat)
  }

  def auth(rh: RequestHeader): Future[Either[Result, AuthedRequest]] = {
    def authJwt = fut(jwtAuth.jwtAuth(rh).map(u => AuthedRequest(u.username, rh, None)))

    def authHtml = bundle.authenticator.authenticate(rh).map(_.left.map(fail => bundle.onUnauthorized(fail)))

    val execAuth: PartialFunction[MediaRange, Future[Either[Result, AuthedRequest]]] = {
      case Accepts.Html() => authHtml
      case PicsController.Json10() => authJwt
      case Accepts.Json() => authJwt
    }

    def acceptedResult(ms: Seq[MediaRange]): Future[Either[Result, AuthedRequest]] = ms match {
      case Nil => fut(Left(NotAcceptable(Errors.single(s"No acceptable '${HeaderNames.ACCEPT}' header found."))))
      case Seq(m, tail@_*) => execAuth.applyOrElse(m, (_: MediaRange) => acceptedResult(tail))
    }

    val accepted =
      if (rh.acceptedTypes.isEmpty) Seq(new MediaRange("*", "*", Nil, None, Nil))
      else rh.acceptedTypes

    acceptedResult(accepted)
  }

  private def fut[T](t: T) = Future.successful(t)
}
