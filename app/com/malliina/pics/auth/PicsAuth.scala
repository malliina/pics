package com.malliina.pics.auth

import akka.stream.Materializer
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics.auth.PicsAuth.log
import com.malliina.pics.{Errors, PicOwner, PicRequest}
import com.malliina.play.auth.{AuthFailure, UserAuthenticator}
import com.malliina.play.controllers.{AuthBundle, OAuthControl}
import controllers.Admin
import play.api.Logger
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

class PicsAuth(val authenticator: PicsAuthLike,
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

  def authed(rh: RequestHeader, authorize: PicRequest => Boolean): Future[Either[Result, PicRequest]] = {
    authenticator.authenticate(rh).map(_.filterOrElse(authorize, unauth(rh)))
  }

  def unauth(rh: RequestHeader) = {
    log.error(s"Auth failed for '$rh'.")
    Unauthorized(Errors.single("Unauthorized."))
  }
}
