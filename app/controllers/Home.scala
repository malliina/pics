package controllers

import java.nio.file.Files

import akka.stream.Materializer
import buildinfo.BuildInfo
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics._
import com.malliina.pics.html.PicsHtml
import com.malliina.play.auth.{AuthFailure, UserAuthenticator}
import com.malliina.play.controllers.{AuthBundle, BaseSecurity, Caching, OAuthControl}
import com.malliina.play.http.AuthedRequest
import controllers.Home._
import org.apache.commons.io.FilenameUtils
import play.api.Logger
import play.api.cache.Cached
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.{HeaderNames, HttpEntity, MimeTypes}
import play.api.libs.json.Json
import play.api.mvc._

case class UserFeedback(message: String, isSuccess: Boolean)

object UserFeedback {
  val ErrorMessage = "error-message"
  val Message = "message"

  def forFlash(flash: Flash) =
    flash.get(ErrorMessage).map(UserFeedback(_, isSuccess = false))
      .orElse(flash.get(Message).map(UserFeedback(_, isSuccess = true)))
}

case class KeyEntry(key: Key, url: Call, thumb: Call)

object KeyEntry {
  def apply(key: Key): KeyEntry =
    KeyEntry(
      key,
      routes.Home.pic(key),
      routes.Home.thumb(key)
    )
}

object Home {
  private val log = Logger(getClass)

  val binaryContentType = ContentType(MimeTypes.BINARY)

  val CreatedKey = "created"
  val KeyKey = "key"
  val Message = "message"
  val Reason = "reason"
  val XKey = "X-Key"

  def auth(oauth: OAuthControl): AuthBundle[AuthedRequest] = {
    val sessionAuth = UserAuthenticator.session(oauth.sessionUserKey)
      .transform((req, user) => Right(AuthedRequest(user, req)))
    new AuthBundle[AuthedRequest] {
      override val authenticator = sessionAuth

      override def onUnauthorized(failure: AuthFailure) =
        Results.Redirect(oauth.startOAuth)
    }
  }

  def security(oauth: OAuthControl, mat: Materializer): BaseSecurity[AuthedRequest] =
    new BaseSecurity(oauth.actions, auth(oauth), mat)
}

class Home(files: PicFiles,
           thumbs: PicFiles,
           resizer: Resizer,
           oauth: Admin,
           cache: Cached,
           security: BaseSecurity[AuthedRequest],
           comps: ControllerComponents) extends AbstractController(comps) {
  val placeHolderResource = "400x300.png"
  val deleteForm: Form[Key] = Form(mapping(KeyKey -> nonEmptyText)(Key.apply)(Key.unapply))

  def ping = Action(Caching.NoCache {
    Ok(Json.obj("name" -> BuildInfo.name, "version" -> BuildInfo.version, "hash" -> BuildInfo.hash))
  })

  def drop = security.authAction { user =>
    val created = user.rh.flash.get(CreatedKey).map(k => KeyEntry(Key(k)))
    Ok(PicsHtml.drop(created, user.user))
  }

  def list = security.authAction { user =>
    val keys = files.load(0, 100)
    val entries = keys map { key => KeyEntry(key) }
    val feedback = UserFeedback.forFlash(user.rh.flash)
    Ok(PicsHtml.pics(entries, feedback, user.user))
  }

  def pic(key: Key) = Action {
    files.find(key).fold(keyNotFound(key))(streamData)
  }

  def thumb(key: Key) = security.authAction { _ =>
    thumbs.find(key).filter(_.isImage)
      .map(streamData)
      .getOrElse(Ok.sendResource(placeHolderResource))
  }

  def delete = security.authenticatedLogged(_ => deleteNoAuth)

  def remove(key: Key) = security.authAction { _ =>
    removeKey(key)
  }

  def removeKey(key: Key): Result = {
    val redir = Redirect(routes.Home.list())
    if (thumbs contains key) {
      thumbs remove key
    }
    if (files contains key) {
      (files remove key).foreach { _ => log info s"Removed '$key'." }
      redir.flashing(UserFeedback.Message -> s"Deleted key '$key'.")
    } else {
      redir.flashing(UserFeedback.ErrorMessage -> s"Key not found: '$key'.")
    }
  }

  def put = security.authenticatedLogged { _ =>
    putNoAuth
  }

  private def deleteNoAuth = Action(parse.form(deleteForm)) { req =>
    removeKey(req.body)
  }

  private def putNoAuth = Action(parse.multipartFormData) { req =>
    req.body.files.headOption map { file =>
      // without dot
      val ext = FilenameUtils.getExtension(file.filename)
      val tempFile = file.ref.path
      val name = tempFile.getFileName.toString
      val renamedFile = tempFile resolveSibling s"$name.$ext"
      Files.move(tempFile, renamedFile)
      val thumbFile = renamedFile resolveSibling s"$name-thumb.$ext"
      resizer.resizeFromFile(renamedFile, thumbFile).fold(
        fail => failResize(fail),
        _ => {
          val key = Key.randomish()
          for {
            _ <- files.put(key, renamedFile)
            _ <- thumbs.put(key, thumbFile)
          } yield ()
          val url = routes.Home.pic(key)
          log info s"Saved file '${file.filename}' as '$key'."
          Accepted(Json.obj(Message -> s"Created '$url'.")).withHeaders(
            HeaderNames.LOCATION -> url.toString,
            XKey -> key.key
          )
        }
      )
    } getOrElse {
      badRequest("File missing")
    }
  }

  def failResize(error: ImageFailure): Result = error match {
    case UnsupportedFormat(format, supported) =>
      val msg = s"Unsupported format: '$format', must be one of: '${supported.mkString(", ")}'"
      log.error(msg)
      badRequest(msg)
    case ImageException(ioe) =>
      val msg = "An I/O error occurred."
      log.error(msg, ioe)
      internalError(msg)
  }

  def logout = security.authAction { _ =>
    oauth.ejectWith(oauth.logoutMessage).withNewSession
  }

  def eject = security.logged(Action { req =>
    Ok(PicsHtml.eject(req.flash.get(oauth.messageKey)))
  })

  def streamData(stream: DataStream) =
    Ok.sendEntity(
      HttpEntity.Streamed(
        stream.source,
        stream.contentLength.map(_.toBytes),
        stream.contentType.map(_.contentType))
    )

  def keyNotFound(key: Key) = onNotFound(s"Not found: $key")

  def onNotFound(message: String) = NotFound(Json.obj(Message -> message))

  def badGateway(reason: String) = BadGateway(reasonJson(reason))

  def badRequest(reason: String) = BadRequest(reasonJson(reason))

  def internalError(reason: String) = InternalServerError(reasonJson(reason))

  def reasonJson(reason: String) = Json.obj(Reason -> reason)

  // cannot cache streamed entities

  //  def pic(key: Key) = cache((req: RequestHeader) => req.path, 3600) {
  //    Action {
  //      files.find(key)
  //        .map(streamData)
  //        .getOrElse(keyNotFound(key))
  //    }
  //  }
}
