package com.malliina.pics.auth

import akka.stream.Materializer
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.{Errors, PicOwner, PicRequest}
import com.malliina.play.auth.{AuthFailure, InvalidCredentials, MissingCredentials, UserAuthenticator}
import com.malliina.play.controllers.{AuthBundle, OAuthControl}
import controllers.{Admin, JWTAuth, PicsController}
import play.api.Logger
import play.api.http.{HeaderNames, MediaRange}
import play.api.libs.streams.Accumulator
import play.api.mvc._

import scala.concurrent.Future

object PicsAuth {
  private val log = Logger(getClass)

  def oauth(oauth: OAuthControl): AuthBundle[PicRequest] = {
    val sessionAuth = UserAuthenticator.session(oauth.sessionUserKey)
      .transform((req, user) => Right(PicRequest(PicOwner(user.name), req)))
    new AuthBundle[PicRequest] {
      override val authenticator = sessionAuth

      override def onUnauthorized(failure: AuthFailure) =
        Results.Redirect(oauth.startOAuth)
    }
  }
}

class PicsAuth(jwtAuth: JWTAuth,
               bundle: AuthBundle[PicRequest],
               mat: Materializer,
               actions: DefaultActionBuilder) extends ControllerHelpers {

  def authAction(a: PicRequest => Future[Result]) =
    authed(req => actions.async(a(req)))

  def ownerAction(a: PicRequest => Future[Result]) =
    ownerAuthed(req => actions.async(a(req)))

  def adminAction(a: PicRequest => Future[Result]) =
    adminAuthed(req => actions.async(a(req)))

  def authed(a: PicRequest => EssentialAction) =
    withAuth(auth, a)

  def ownerAuthed(a: PicRequest => EssentialAction) =
    withAuth(rh => authed(rh, u => !u.readOnly), a)

  def adminAuthed(a: PicRequest => EssentialAction) =
    withAuth(rh => authed(rh, _.name == Admin.AdminUser), a)

  def withAuth(check: RequestHeader => Future[Either[Result, PicRequest]], a: PicRequest => EssentialAction) =
    EssentialAction { rh =>
      val futAcc = check(rh).map(_.fold(
        err => actions(err)(rh),
        req => a(req)(rh)
      ))
      Accumulator.flatten(futAcc)(mat)
    }

  def auth(rh: RequestHeader): Future[Either[Result, PicRequest]] =
    authed(rh, _ => true)

  def authed(rh: RequestHeader, authorize: PicRequest => Boolean) = {
    def authJwt = {
      val res = jwtAuth.userOrAnon(rh).filterOrElse(authorize, unauth)
      fut(res)
    }

    def authHtml: Future[Either[Result, PicRequest]] = bundle.authenticator.authenticate(rh).map { e =>
      e.fold(
        {
          case MissingCredentials(req) => Right(PicRequest.anon(req))
          case other => Left(bundle.onUnauthorized(other))
        },
        u => Right(u)
      ).filterOrElse(authorize, bundle.onUnauthorized(InvalidCredentials(rh)))
    }

    val execAuth: PartialFunction[MediaRange, Future[Either[Result, PicRequest]]] = {
      case Accepts.Html() => authHtml
      case PicsController.Json10() => authJwt
      case Accepts.Json() => authJwt
    }

    def acceptedResult(ms: Seq[MediaRange]): Future[Either[Result, PicRequest]] = ms match {
      case Nil => fut(Left(NotAcceptable(Errors.single(s"No acceptable '${HeaderNames.ACCEPT}' header found."))))
      case Seq(m, tail@_*) => execAuth.applyOrElse(m, (_: MediaRange) => acceptedResult(tail))
    }

    val accepted =
      if (rh.acceptedTypes.isEmpty) Seq(new MediaRange("*", "*", Nil, None, Nil))
      else rh.acceptedTypes

    acceptedResult(accepted)
  }

  def unauth = Unauthorized(Errors.single("Unauthorized."))

  private def fut[T](t: T) = Future.successful(t)
}
