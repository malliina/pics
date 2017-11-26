package controllers

import java.nio.file.Files

import akka.stream.Materializer
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics._
import com.malliina.pics.html.PicsHtml
import com.malliina.play.auth.{AuthFailure, UserAuthenticator}
import com.malliina.play.controllers._
import com.malliina.play.http.AuthedRequest
import com.malliina.play.models.Username
import controllers.Home._
import org.apache.commons.io.FilenameUtils
import play.api.Logger
import play.api.cache.Cached
import play.api.data.Form
import play.api.data.Forms._
import play.api.http._
import play.api.libs.json.{Json, Writes}
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object Home {
  private val log = Logger(getClass)

  val binaryContentType = ContentType(MimeTypes.BINARY)
  val Json10 = Accepting("application/vnd.pics.v10+json")

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

  def ping = Action(Caching.NoCache(Ok(Json.toJson(AppMeta.default))))

  def root = Action(Redirect(routes.Home.list()))

  def list = parsedNoAuth(ListRequest.forRequest) { req =>
    val keys = files.load(req.offset, req.limit)
    val entries = keys map { key => KeyEntry(key, req.rh) }

    renderContent(req.rh)(
      json = Pics(entries),
      html = {
        val feedback = UserFeedback.flashed(req.rh.flash)
        PicsHtml.pics(entries, feedback, req.user)
      }
    )
  }

  def drop = security.authAction { user =>
    val created = user.rh.flash.get(CreatedKey).map(k => KeyEntry(Key(k), user.rh))
    val feedback = UserFeedback.flashed(user.rh.flash)
    Ok(PicsHtml.drop(created, feedback, user.user))
  }

  def parsed[T](parse: AuthedRequest => Either[Errors, T])(f: T => Result) =
    security.authAction { req =>
      parse(req).fold(
        errors => BadRequest(Json.toJson(errors)),
        t => f(t))
    }

  def parsedNoAuth[T](parse: AuthedRequest => Either[Errors, T])(f: T => Result) =
    Action { req =>
      val r = AuthedRequest(Username("demo"), req, None)
      parse(r).fold(
        errors => BadRequest(Json.toJson(errors)),
        t => f(t))
    }

  def renderContent[A: Writes, B: Writeable](rh: RequestHeader)(json: => A, html: => B) =
    if (rh.getQueryString("f").contains("json")) {
      Ok(Json.toJson(json))
    } else {
      renderVaried(rh) {
        case Accepts.Html() => Ok(html)
        case Home.Json10() => Ok(Json.toJson(json))
        case Accepts.Json() => Ok(Json.toJson(json))
      }
    }

  def renderVaried(rh: RequestHeader)(f: PartialFunction[MediaRange, Result]) = render(f)(rh)

  def pic(key: Key) = picAction(files.find(key), keyNotFound(key))

  def thumb(key: Key) = security.authenticatedLogged { _ =>
    picAction(thumbs.find(key).map(_.filter(_.isImage)), Ok.sendResource(placeHolderResource))
  }

  private def picAction(find: Future[Option[DataResponse]], onNotFound: => Result) = {
    val result = find.map { maybe =>
      maybe.map {
        case DataFile(file, _, _) => Ok.sendPath(file)
        case s@DataStream(_, _, _) => streamData(s)
      }.getOrElse {
        onNotFound
      }
    }
    Action.async(result)
  }

  private def streamData(stream: DataStream) =
    Ok.sendEntity(
      HttpEntity.Streamed(
        stream.source,
        stream.contentLength.map(_.toBytes),
        stream.contentType.map(_.contentType))
    )

  def delete = security.authenticatedLogged(_ => deleteNoAuth)

  def remove(key: Key) = security.authAction { _ =>
    removeKey(key, routes.Home.list())
  }

  def removeKey(key: Key, redirCall: Call): Result = {
    val redir = Redirect(redirCall)
    if (thumbs contains key) {
      thumbs remove key
    }
    if (files contains key) {
      (files remove key).foreach { _ => log info s"Removed '$key'." }
      redir.flashing(toMap(UserFeedback(s"Deleted key '$key'.", isError = false)): _*)
    } else {
      redir.flashing(toMap(UserFeedback(s"Key not found: '$key'.", isError = true)): _*)
    }
  }

  def toMap(fb: UserFeedback): Seq[(String, String)] = Seq(
    UserFeedback.Feedback -> fb.message,
    UserFeedback.Success -> (if(fb.isError) UserFeedback.No else UserFeedback.Yes)
  )

  def put = security.authenticatedLogged { _ =>
    putNoAuth
  }

  private def deleteNoAuth = Action(parse.form(deleteForm)) { req =>
    removeKey(req.body, routes.Home.drop())
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
          val key = Key.randomish().append(s".${ext.toLowerCase}")
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

  def keyNotFound(key: Key) = onNotFound(s"Not found: $key")

  def onNotFound(message: String) = NotFound(Json.obj(Message -> message))

  def badGateway(reason: String) = BadGateway(reasonJson(reason))

  def badRequest(reason: String) = BadRequest(reasonJson(reason))

  def internalError(reason: String) = InternalServerError(reasonJson(reason))

  def reasonJson(reason: String) = Json.obj(Reason -> reason)

  // cannot cache streamed entities

  def cachedAction(result: Result) = cache((req: RequestHeader) => req.path, 180.days)(Action(result))
}
