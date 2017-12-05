package controllers

import java.nio.file.Files
import java.time.Instant

import akka.stream.Materializer
import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics._
import com.malliina.pics.auth.CognitoValidator
import com.malliina.pics.db.PicsDb
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

class Home(db: PicsDb,
           files: FlatFiles,
           thumbs: FlatFiles,
           resizer: Resizer,
           oauth: Admin,
           cache: Cached,
           security: BaseSecurity[AuthedRequest],
           comps: ControllerComponents) extends AbstractController(comps) {
  val placeHolderResource = "400x300.png"
  val deleteForm: Form[Key] = Form(mapping(KeyKey -> nonEmptyText)(Key.apply)(Key.unapply))
  val jwt = new JWTController(CognitoValidator.default, comps)

  def ping = Action(Caching.NoCache(Ok(Json.toJson(AppMeta.default))))

  def root = Action(Redirect(routes.Home.list()))

  def list = parsed(ListRequest.forRequest) { req =>
    db.load(req.offset, req.limit, req.user).map { keys =>
      val entries = keys map { key => KeyEntry(key, req.rh) }
      renderContent(req.rh)(
        json = Pics(entries),
        html = {
          val feedback = UserFeedback.flashed(req.rh.flash)
          PicsHtml.pics(entries, feedback, req.user)
        }
      )
    }
  }

  def drop = security.authAction { user =>
    val created = user.rh.flash.get(CreatedKey).map { k =>
      KeyEntry(KeyMeta(Key(k), user.user, Instant.now()), user.rh)
    }
    val feedback = UserFeedback.flashed(user.rh.flash)
    Ok(PicsHtml.drop(created, feedback, user.user))
  }

  def sync = security.authActionAsync { _ =>
    Syncer.sync(files, db).map { count =>
      Redirect(routes.Home.drop()).flashing(toMap(UserFeedback.success(s"Synced $count assets.")): _*)
    }
  }

  def parsed[T](parse: AuthedRequest => Either[Errors, T])(f: T => Future[Result]) =
    withAuthAction { req =>
      parse(req).fold(
        errors => fut(BadRequest(Json.toJson(errors))),
        t => f(t)
      )
    }

  private def withAuthAction(a: AuthedRequest => Future[Result]) =
    withAuth(req => Action.async(a(req)))

  private def withAuth(a: AuthedRequest => EssentialAction) = EssentialAction { rh =>
    val f: PartialFunction[MediaRange, EssentialAction] = {
      case Accepts.Html() => security.authenticatedLogged(a)
      case Home.Json10() => jwt.authed(cognitoUser => a(AuthedRequest(cognitoUser.username, rh, None)))
      case Accepts.Json() => jwt.authed(cognitoUser => a(AuthedRequest(cognitoUser.username, rh, None)))
    }

    def acceptedAction(ms: Seq[MediaRange]): EssentialAction = ms match {
      case Nil => Action(NotAcceptable(Errors.single(s"No acceptable '${HeaderNames.ACCEPT}' header found.")))
      case Seq(m, tail@_*) => f.applyOrElse(m, (_: MediaRange) => acceptedAction(tail))
    }

    val accepted =
      if (rh.acceptedTypes.isEmpty) Seq(new MediaRange("*", "*", Nil, None, Nil))
      else rh.acceptedTypes
    acceptedAction(accepted)(rh)
  }

  def parsedNoAuth[T](parse: AuthedRequest => Either[Errors, T])(f: T => Future[Result]) =
    Action.async { req =>
      val r = AuthedRequest(Username("demo"), req, None)
      parse(r).fold(
        errors => fut(BadRequest(Json.toJson(errors))),
        t => f(t)
      )
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

  def thumbAuth(key: Key) = security.authenticatedLogged { _ =>
    picAction(thumbs.find(key).map(_.filter(_.isImage)), Ok.sendResource(placeHolderResource))
  }

  def thumb(key: Key) = picAction(thumbs.find(key).map(_.filter(_.isImage)), Ok.sendResource(placeHolderResource))

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

  def delete = security.authenticatedLogged { (r: AuthedRequest) =>
    Action.async(parse.form(deleteForm)) { req =>
      removeKey(req.body, r.user, routes.Home.drop())
    }
  }

  def remove(key: Key) = security.authActionAsync { r =>
    removeKey(key, r.user, routes.Home.list())
  }

  def removeKey(key: Key, user: Username, redirCall: Call): Future[Result] = {
    val redir = Redirect(redirCall)
    for {
      _ <- db.remove(key, user)
      _ <- removeThumb(key, user)
      wasRemoved <- removeOriginal(key, user)
    } yield {
      if (wasRemoved) redir.flashing(toMap(UserFeedback.success(s"Deleted key '$key'.")): _*)
      else redir.flashing(toMap(UserFeedback.error(s"Key not found: '$key'.")): _*)
    }
  }

  private def removeThumb(key: Key, user: Username): Future[Unit] = {
    thumbs.contains(key).flatMap { exists =>
      if (exists) thumbs.remove(key)
      else Future.successful(())
    }
  }

  private def removeOriginal(key: Key, user: Username): Future[Boolean] = {
    files.contains(key).flatMap { exists =>
      if (exists) {
        files.remove(key).map { _ =>
          log info s"Removed '$key'."
          true
        }
      } else {
        fut(false)
      }
    }
  }


  def toMap(fb: UserFeedback): Seq[(String, String)] = Seq(
    UserFeedback.Feedback -> fb.message,
    UserFeedback.Success -> (if (fb.isError) UserFeedback.No else UserFeedback.Yes)
  )

  def put = security.authenticatedLogged { _ =>
    putNoAuth
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
            _ <- db.put(key, thumbFile)
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

  def fut[T](t: T) = Future.successful(t)
}
