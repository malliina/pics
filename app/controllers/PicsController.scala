package controllers

import java.nio.file.Path
import java.time.Instant

import com.malliina.concurrent.Execution.cached
import com.malliina.html.UserFeedback
import com.malliina.pics.LoginStrings.AuthFailed
import com.malliina.pics._
import com.malliina.pics.auth.PicsAuth
import com.malliina.pics.html.PicsHtml
import com.malliina.play.auth.AccessToken
import com.malliina.play.controllers._
import com.malliina.play.http.Proxies
import com.sksamuel.scrimage.ImageParseException
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
  val JsonAccept = Accepting(MimeTypes.JSON)
  val HtmlAccept = Accepting(MimeTypes.HTML)

  val CreatedKey = "created"
  val KeyKey = "key"
  val Message = "message"
  val Reason = "reason"
  val XKey = "X-Key"
  val XName = "X-Name"
  val XClientPic = "X-Client-Pic"
}

class PicsController(html: PicsHtml,
                     pics: PicService,
                     picSink: PicSink,
                     auth: PicsAuth,
                     cache: Cached,
                     comps: ControllerComponents) extends AbstractController(comps) {

  case class TokenForm(token: Option[String], error: Option[String]) {
    def toEither =
      error.map(e => Left(e))
        .orElse(token.map(t => Right(AccessToken(t))))
        .getOrElse(Left(AuthFailed))
  }

  val placeHolderResource = "400x300.png"
  val deleteForm: Form[Key] = Form(mapping(KeyKey -> nonEmptyText)(Key.apply)(Key.unapply))
  val tokenForm: Form[TokenForm] = Form(mapping(
    "token" -> optional(nonEmptyText),
    "error" -> optional(nonEmptyText)
  )(TokenForm.apply)(TokenForm.unapply))
  val reverse = routes.PicsController
  val metaDatabase = pics.metaDatabase
  val sources = pics.handler

  def ping = Action(Caching.NoCache(Ok(Json.toJson(AppMeta.default))))

  def version = Action { req =>
    renderVaried(req) {
      case Json10() => Ok(Json.toJson(AppMeta.default))
    }
  }

  def root = Action(Redirect(reverse.list()))

  def signIn = Action { req =>
    import Social._
    val call = req.cookies.get(ProviderCookie).flatMap(c => AuthProvider.forString(c.value).toOption).map {
      case Google => routes.Social.google()
      case Microsoft => routes.Social.microsoft()
      case Facebook => routes.Social.facebook()
      case GitHub => routes.Social.github()
      case Amazon => routes.Social.amazon()
      case Twitter => routes.Social.twitter()
    }
    call.map(c => Redirect(c)).getOrElse(Ok(html.signIn()))
  }

  def postSignIn = Action(parse.form(tokenForm)) { req =>
    req.body.toEither.fold(
      err => {
        failForm(err, req)
      },
      token => {
        auth.authenticator.validateToken(token).fold(
          _ => {
            failForm(LoginStrings.AuthFailed, req)
          },
          user => {
            Redirect(reverse.list()).withSession("username" -> user.username.name)
          }
        )
      }
    )
  }

  private def failForm(message: String, rh: RequestHeader): Result = {
    log.error(s"Form authentication failed from '$rh'.")
    BadRequest(html.signIn(Option(UserFeedback.error(message))))
  }

  def list = parsed(ListRequest.forRequest) { req =>
    metaDatabase.load(req.offset, req.limit, req.user.name).map { keys =>
      val entries = keys map { key => PicMetas(key, req.rh) }
      renderContent(req.rh)(
        json = {
          Pics(entries)
        },
        html = {
          val feedback = UserFeedbacks.flashed(req.rh.flash)
          html.pics(entries, feedback, req.user)
        }
      )
    }
  }

  def drop = auth.authAction { user =>
    val created = user.rh.flash.get(CreatedKey).map { k =>
      PicMetas(KeyMeta(Key(k), user.name, Instant.now()), user.rh)
    }
    val feedback = UserFeedbacks.flashed(user.rh.flash)
    fut(Ok(html.drop(created, feedback, user)))
  }

  def sync = auth.adminAction { _ =>
    Syncer.sync(sources.originals.storage, metaDatabase).map { count =>
      Redirect(reverse.drop()).flashing(UserFeedback.success(s"Synced $count assets.").toMap: _*)
    }
  }

  def pic(key: Key) = picAction(sources.originals.storage.find(key), keyNotFound(key))

  def small(key: Key) = sendPic(key, sources.smalls.storage)

  def medium(key: Key) = sendPic(key, sources.mediums.storage)

  def large(key: Key) = sendPic(key, sources.larges.storage)

  def sendPic(key: Key, source: DataSource) =
    picAction(source.find(key).map(_.filter(_.isImage)), Ok.sendResource(placeHolderResource))

  def put = auth.authed { (user: PicRequest) =>
    Action(parse.temporaryFile).async { req =>
      log.info(s"Received file. Resizing and uploading...")
      saveFile(req.body.path, user)
    }
  }

  def delete = auth.ownerAuthed { (user: PicRequest) =>
    Action.async(parse.form(deleteForm)) { req =>
      removeKey(req.body, user, reverse.drop())
    }
  }

  def remove(key: Key) = auth.ownerAction { user =>
    removeKey(key, user, reverse.list())
  }

  def deleteKey(key: Key) = remove(key)

  def privacyPolicy = Action(Ok(html.privacyPolicy))

  def support = Action(Ok(html.support))

  private def removeKey(key: Key, user: PicRequest, redirCall: Call): Future[Result] =
    metaDatabase.remove(key, user.name).flatMap { wasDeleted =>
      if (wasDeleted) {
        log.info(s"Key '$key' removed by '${user.name}' from '${Proxies.realAddress(user.rh)}'.")
        picSink.onPicRemoved(key, user)
        sources.remove(key).map { _ =>
          renderResult(user.rh)(
            json = Accepted,
            html = Redirect(redirCall).flashing(UserFeedback.success(s"Deleted key '$key'.").toMap: _*)
          )
        }
      } else {
        log.error(s"Key not found: '$key'.")
        fut(keyNotFound(key))
      }
    }

  def parsed[T](parse: PicRequest => Either[Errors, T])(f: T => Future[Result]) =
    auth.authAction { req =>
      parse(req).fold(
        errors => fut(BadRequest(errors)),
        t => f(t)
      )
    }

  def renderContent[A: Writes, B: Writeable](rh: RequestHeader)(json: => A, html: => B) =
    renderResult(rh)(Ok(Json.toJson(json)), Ok(html))

  def renderResult(rh: RequestHeader)(json: => Result, html: => Result): Result = {
    if (rh.getQueryString("f").contains("json")) {
      json
    } else {
      renderVaried(rh) {
        case HtmlAccept() => html
        case Json10() => json
        case JsonAccept() => json
      }
    }
  }

  def renderVaried(rh: RequestHeader)(f: PartialFunction[MediaRange, Result]) = render(f)(rh)

  private def picAction(find: Future[Option[DataResponse]], onNotFound: => Result): Action[AnyContent] = {
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

  private def saveFile(tempFile: Path, by: PicRequest): Future[Result] = {
    val rh = by.rh
    pics.save(tempFile, by, rh.headers.get(XName)).map { meta =>
      val clientPic = rh.headers.get(XClientPic).map(Key.apply).getOrElse(meta.key)
      val picMeta = PicMetas(meta, rh)
      picSink.onPic(picMeta.withClient(clientPic), by)
      log info s"Saved '${picMeta.key}' with URL '${picMeta.url}'."
      Accepted(PicResponse(picMeta)).withHeaders(
        HeaderNames.LOCATION -> picMeta.url.toString,
        XKey -> meta.key.key,
        XClientPic -> clientPic.key
      )
    }.recover {
      case t: ImageFailure => failResize(t, by)
      case ipa: ImageParseException => failResize(ResizeException(ipa), by)
    }
  }

  def failResize(error: ImageFailure, by: PicRequest): Result = error match {
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
    case ResizeException(ipa) =>
      log.error(s"Unable to parse image by '${by.name}'.", ipa)
      badRequest("Unable to parse image.")
  }

  def keyNotFound(key: Key) = onNotFound(s"Not found: '$key'.")

  def onNotFound(message: String) = NotFound(reasonJson(message))

  def badGateway(reason: String) = BadGateway(reasonJson(reason))

  def badRequest(reason: String) = BadRequest(reasonJson(reason))

  def unauthorized(reason: String) = Unauthorized(reasonJson(reason))

  def internalError(reason: String) = InternalServerError(reasonJson(reason))

  def reasonJson(reason: String) = Errors.single(reason)

  // cannot cache streamed entities

  def cachedAction(result: Result) = cache((req: RequestHeader) => req.path, 180.days)(Action(result))

  def fut[T](t: T) = Future.successful(t)
}
