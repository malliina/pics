package controllers

import java.nio.file.Path
import java.time.Instant

import com.malliina.concurrent.ExecutionContexts.cached
import com.malliina.pics._
import com.malliina.pics.auth.PicsAuth
import com.malliina.pics.html.PicsHtml
import com.malliina.play.auth.{AuthFailure, UserAuthenticator}
import com.malliina.play.controllers._
import com.malliina.play.http.AuthedRequest
import com.malliina.play.models.Username
import controllers.PicsController._
import play.api.Logger
import play.api.cache.Cached
import play.api.data.Form
import play.api.data.Forms._
import play.api.http._
import play.api.libs.json.{Json, Writes}
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object PicsController {
  private val log = Logger(getClass)

  val binaryContentType = ContentType(MimeTypes.BINARY)
  val Json10 = Accepting("application/vnd.pics.v10+json")

  val CreatedKey = "created"
  val KeyKey = "key"
  val Message = "message"
  val Reason = "reason"
  val XKey = "X-Key"
  val XName = "X-Name"

  def auth(oauth: OAuthControl): AuthBundle[AuthedRequest] = {
    val sessionAuth = UserAuthenticator.session(oauth.sessionUserKey)
      .transform((req, user) => Right(AuthedRequest(user, req)))
    new AuthBundle[AuthedRequest] {
      override val authenticator = sessionAuth

      override def onUnauthorized(failure: AuthFailure) =
        Results.Redirect(oauth.startOAuth)
    }
  }
}

class PicsController(html: PicsHtml,
                     pics: PicService,
                     picSink: PicSink,
                     auth: PicsAuth,
                     cache: Cached,
                     comps: ControllerComponents) extends AbstractController(comps) {
  val placeHolderResource = "400x300.png"
  val deleteForm: Form[Key] = Form(mapping(KeyKey -> nonEmptyText)(Key.apply)(Key.unapply))
  val reverse = routes.PicsController
  val metaDatabase = pics.metaDatabase
  val sources = pics.sources

  def ping = Action(Caching.NoCache(Ok(Json.toJson(AppMeta.default))))

  def root = Action(Redirect(reverse.list()))

  def list = parsed(ListRequest.forRequest) { req =>
    metaDatabase.load(req.offset, req.limit, req.user).map { keys =>
      val entries = keys map { key => PicMetas(key, req.rh) }
      renderContent(req.rh)(
        json = {
          Pics(entries)
        },
        html = {
          val feedback = UserFeedback.flashed(req.rh.flash)
          html.pics(entries, feedback, req.user)
        }
      )
    }
  }

  def drop = auth.authAction { user =>
    val created = user.rh.flash.get(CreatedKey).map { k =>
      PicMetas(KeyMeta(Key(k), user.user, Instant.now()), user.rh)
    }
    val feedback = UserFeedback.flashed(user.rh.flash)
    fut(Ok(html.drop(created, feedback, user.user)))
  }

  def sync = auth.authAction { _ =>
    Syncer.sync(sources.originals, metaDatabase).map { count =>
      Redirect(reverse.drop()).flashing(toMap(UserFeedback.success(s"Synced $count assets.")): _*)
    }
  }

  def pic(key: Key) = picAction(sources.originals.find(key), keyNotFound(key))

  def small(key: Key) = sendPic(key, sources.smalls)

  def medium(key: Key) = sendPic(key, sources.mediums)

  def large(key: Key) = sendPic(key, sources.larges)

  def sendPic(key: Key, source: DataSource) =
    picAction(source.find(key).map(_.filter(_.isImage)), Ok.sendResource(placeHolderResource))

  def put = auth.authed { (authedRequest: AuthedRequest) =>
    Action(parse.temporaryFile).async { req =>
      log.info(s"Received file. Resizing and uploading...")
      saveFile(req.body.path, authedRequest.user, req)
    }
  }

  def delete = auth.authed { (authedRequest: AuthedRequest) =>
    Action.async(parse.form(deleteForm)) { req =>
      removeKey(req.body, authedRequest.user, reverse.drop())
    }
  }

  def remove(key: Key) = auth.authAction { r =>
    removeKey(key, r.user, reverse.list())
  }

  def removeKey(key: Key, user: Username, redirCall: Call): Future[Result] = {
    val redir = Redirect(redirCall)
    val feedback: Future[UserFeedback] =
      metaDatabase.remove(key, user).flatMap { wasDeleted =>
        if (wasDeleted) {
          sources.remove(key).map { _ =>
            UserFeedback.success(s"Deleted key '$key'.")
          }
        } else {
          fut(UserFeedback.error(s"Key not found: '$key'."))
        }
      }
    feedback.map { fb => redir.flashing(toMap(fb): _*) }
  }

  def parsed[T](parse: AuthedRequest => Either[Errors, T])(f: T => Future[Result]) =
    auth.authAction { req =>
      parse(req).fold(
        errors => fut(BadRequest(errors)),
        t => f(t)
      )
    }

  def renderContent[A: Writes, B: Writeable](rh: RequestHeader)(json: => A, html: => B) =
    if (rh.getQueryString("f").contains("json")) {
      Ok(Json.toJson(json))
    } else {
      renderVaried(rh) {
        case Accepts.Html() => Ok(html)
        case PicsController.Json10() => Ok(Json.toJson(json))
        case Accepts.Json() => Ok(Json.toJson(json))
      }
    }

  def renderVaried(rh: RequestHeader)(f: PartialFunction[MediaRange, Result]) = render(f)(rh)

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

  private def saveFile(tempFile: Path, by: Username, rh: RequestHeader): Future[Result] = {
    pics.save(tempFile, by, rh.headers.get(XName)).map(_.fold(
      fail => failResize(fail),
      meta => {
        val picMeta = PicMetas(meta, rh)
        picSink.onPic(picMeta, by)
        log info s"Saved '${picMeta.key}' with URL '${picMeta.url}'."
        Accepted(PicResponse(picMeta)).withHeaders(
          HeaderNames.LOCATION -> picMeta.url.toString,
          XKey -> meta.key.key
        )
      }
    ))
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
    case ImageReaderFailure(file) =>
      log.error(s"Unable to read image from file '$file'")
      badRequest("Unable to read image.")
  }

  def keyNotFound(key: Key) = onNotFound(s"Not found: $key")

  def onNotFound(message: String) = NotFound(Json.obj(Message -> message))

  def badGateway(reason: String) = BadGateway(reasonJson(reason))

  def badRequest(reason: String) = BadRequest(reasonJson(reason))

  def unauthorized(reason: String) = Unauthorized(reasonJson(reason))

  def internalError(reason: String) = InternalServerError(reasonJson(reason))

  def reasonJson(reason: String) = Json.obj(Reason -> reason)

  // cannot cache streamed entities

  def cachedAction(result: Result) = cache((req: RequestHeader) => req.path, 180.days)(Action(result))

  def fut[T](t: T) = Future.successful(t)

  private def toMap(fb: UserFeedback): Seq[(String, String)] = Seq(
    UserFeedback.Feedback -> fb.message,
    UserFeedback.Success -> (if (fb.isError) UserFeedback.No else UserFeedback.Yes)
  )
}
