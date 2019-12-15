package com.malliina.pics.auth

import akka.stream.Materializer
import com.malliina.concurrent.Execution.cached
import com.malliina.pics.auth.PicsAuth.log
import com.malliina.pics.{Errors, PicOwner, PicRequest}
import com.malliina.play.controllers.AuthBundle
import controllers.Social
import play.api.Logger
import play.api.libs.streams.Accumulator
import play.api.mvc._

import scala.concurrent.Future

object PicsAuth {
  private val log = Logger(getClass)

  val AdminUser = PicOwner("malliina123@gmail.com")

  def social = oauth(Social.SessionKey)

  def oauth(sessionKey: String): AuthBundle[PicRequest] =
    AuthBundle.oauth(
      (req, user) => PicRequest(PicOwner(user.name), req),
      controllers.routes.PicsController.signIn(),
      sessionKey
    )
}

class PicsAuth(val authenticator: PicsAuthLike, mat: Materializer, actions: DefaultActionBuilder)
  extends ControllerHelpers {

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
    withAuth(rh => authed(rh, _.name == PicsAuth.AdminUser), a)

  def withAuth(
    check: RequestHeader => Future[Either[Result, PicRequest]],
    a: PicRequest => EssentialAction
  ) =
    EssentialAction { rh =>
      val futAcc = check(rh).map(
        _.fold(
          err => actions(err)(rh),
          req => a(req)(rh)
        )
      )
      Accumulator.flatten(futAcc)(mat)
    }

  def auth(rh: RequestHeader): Future[Either[Result, PicRequest]] =
    authed(rh, _ => true)

  def authed(
    rh: RequestHeader,
    authorize: PicRequest => Boolean
  ): Future[Either[Result, PicRequest]] = {
    authenticator.authenticate(rh).map(_.filterOrElse(authorize, unauth(rh)))
  }

  def unauth(rh: RequestHeader) = {
    log.error(s"Auth failed for '$rh'.")
    Unauthorized(Errors.single("Unauthorized."))
  }
}
