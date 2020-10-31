package controllers

import java.nio.file.{Files, Path}
import java.time.Instant

import com.malliina.concurrent.Execution.cached
import com.malliina.html.UserFeedback
import com.malliina.pics._
import com.malliina.pics.auth.PicsAuth
import com.malliina.pics.html.PicsHtml
import com.malliina.play.controllers._
import com.malliina.play.http.Proxies
import com.malliina.values.AccessToken
import com.sksamuel.scrimage.ImageParseException
import controllers.PicsController._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.http._
import play.api.libs.json.{Json, Writes}
import play.api.mvc._
import com.malliina.storage.StorageLong
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object PicsController {
  private val log = Logger(getClass)

  val binaryContentType = ContentType(MimeTypes.BINARY)
  val Json10 = Accepting("application/vnd.pics.v10+json")
  val JsonAccept = Accepting(MimeTypes.JSON)
  val HtmlAccept = Accepting(MimeTypes.HTML)

  val CreatedKey = "created"
  val Message = "message"
  val Reason = "reason"
  val XClientPic = "X-Client-Pic"
}

class PicsController(
  html: PicsHtml,
  pics: PicService,
  picSink: PicSink,
  auth: PicsAuth,
  comps: ControllerComponents
) extends AbstractController(comps)
  with PicsStrings {

  val placeHolderResource = "400x300.png"
  val deleteForm: Form[Key] = Form(mapping(PicsHtml.KeyKey -> nonEmptyText)(Key.apply)(Key.unapply))
  val tokenForm: Form[AccessToken] = Form(
    mapping(LoginStrings.TokenKey -> nonEmptyText)(AccessToken.apply)(AccessToken.unapply)
  )
  val reverse = ??? // routes.PicsController
  val reverseSocial = ??? // routes.Social
  val metaDatabase = pics.metaDatabase
  val sources = pics.handler

//  def ping = Action(Caching.NoCache(Ok(Json.toJson(AppMeta.default))))

//  def version = Action { req =>
//    renderVaried(req) {
//      case Json10() => Ok(Json.toJson(AppMeta.default))
//    }
//  }

//  def root = Action(Redirect(reverse.list()))

  def signIn = Action { req =>
    import Social._
    val call =
      req.cookies.get(ProviderCookie).flatMap(c => AuthProvider.forString(c.value).toOption).map {
//        case Google    => reverseSocial.google()
//        case Microsoft => reverseSocial.microsoft()
//        case Facebook  => reverseSocial.facebook()
//        case GitHub    => reverseSocial.github()
//        case Amazon    => reverseSocial.amazon()
//        case Twitter   => reverseSocial.twitter()
//        case Apple     => reverseSocial.apple()
        case _ => ???
      }
    call.map(c => Redirect(c)).getOrElse(Ok(html.signIn()))
  }

  def signUp = Action { Ok(html.signUp()) }

  def postSignIn = Action.async(parse.form(tokenForm)) { req =>
    val token = req.body
    auth.authenticator
      .validateToken(token)
      .map { e =>
        e.fold(
          _ => {
            failLogin(LoginStrings.AuthFailed, req)
          },
          user => {
//            Redirect(reverse.list()).withSession(Social.SessionKey -> user.username.name)
            Redirect(???).withSession(Social.SessionKey -> user.username.name)
          }
        )
      }
  }

  private def failLogin(message: String, rh: RequestHeader): Result = {
    log.error(s"Form authentication failed from '$rh'.")
    BadRequest(html.signIn(Option(UserFeedback.error(message))))
  }

  def list = parsed(ListRequest.forRequest) { req =>
    metaDatabase.load(req.offset, req.limit, req.user.name).map { keys =>
      val entries = keys map { key =>
        PicMetas(key, req.rh)
      }
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

  def profile = Action(Ok(html.profile))

  def sync = auth.adminAction { _ =>
    Syncer.sync(sources.originals.storage, metaDatabase).map { count =>
//      Redirect(reverse.drop()).flashing(UserFeedback.success(s"Synced $count assets.").toMap: _*)
      Redirect(???).flashing(UserFeedback.success(s"Synced $count assets.").toMap: _*)
    }
  }

  def pic(key: Key) = Action.async { rh =>
    val isImage = ContentType.parse(key.key).exists(_.isImage)
    PicSize(rh)
      .map { size =>
        sources(size).storage
          .find(key)
          .map(df =>
            df.map(send)
              .getOrElse(
                if (isImage) keyNotFound(key)
                else Ok.sendResource(placeHolderResource)
              )
          )
      }
      .recover { err =>
        Future.successful(badRequest(err.message))
      }
  }

  def small(key: Key) = sendPic(key, sources.smalls.storage)

  def medium(key: Key) = sendPic(key, sources.mediums.storage)

  def large(key: Key) = sendPic(key, sources.larges.storage)

  def sendPic(key: Key, source: DataSource) =
    picAction(_ => source.find(key).map(_.filter(_.isImage)), Ok.sendResource(placeHolderResource))

  def put = auth.authed { user =>
    Action(parse.temporaryFile).async { req =>
      log.info(s"Received file from '${user.name}'. Resizing and uploading...")
      saveFile(req.body.path, user)
    }
  }

  def delete = auth.ownerAuthed { user =>
    Action.async(parse.form(deleteForm)) { req =>
//      removeKey(req.body, user, reverse.drop())
      removeKey(req.body, user, ???)
    }
  }

  def remove(key: Key) = auth.ownerAction { user =>
//    removeKey(key, user, reverse.list())
    removeKey(key, user, ???)
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
            html =
              Redirect(redirCall).flashing(UserFeedback.success(s"Deleted key '$key'.").toMap: _*)
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
    if (rh.getQueryString("f").contains("json") || rh.getQueryString("json").isDefined) {
      json
    } else {
      renderVaried(rh) {
        case HtmlAccept() => html
        case Json10()     => json
        case JsonAccept() => json
      }
    }
  }

  def renderVaried(rh: RequestHeader)(f: PartialFunction[MediaRange, Result]) = render(f)(rh)

  private def picAction(
    find: RequestHeader => Future[Option[DataFile]],
    onNotFound: => Result
  ): Action[AnyContent] =
    Action.async { rh =>
      find(rh).map { maybe =>
        maybe
          .map(df => send(df))
          .getOrElse(onNotFound)
      }
    }

  private def send(df: DataFile) =
    Ok.sendPath(df.file)
      .withHeaders(HeaderNames.CACHE_CONTROL -> "public, max-age=31536000")

  private def saveFile(tempFile: Path, by: PicRequest): Future[Result] = {
    val rh = by.rh
    pics
      .save(tempFile, by, rh.headers.get(XName))
      .map { meta =>
        val clientPic = rh.headers.get(XClientPic).map(Key.apply).getOrElse(meta.key)
        val picMeta = PicMetas(meta, rh)
        // TODO Use akka-streams
        picSink.onPic(picMeta.withClient(clientPic), by)
        log info s"Saved '${picMeta.key}' by '${by.name}' with URL '${picMeta.url}'."
        Accepted(PicResponse(picMeta)).withHeaders(
          HeaderNames.LOCATION -> picMeta.url.toString,
          XKey -> meta.key.key,
          XClientPic -> clientPic.key
        )
      }
      .recover {
        case t: ImageFailure          => failResize(t, by)
        case ipa: ImageParseException => failResize(ResizeException(ipa), by)
        case iae: IllegalArgumentException =>
          log.warn("Failed to read image.", iae)
          failResize(ImageReaderFailure(tempFile), by)
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
      val size = Files.size(file).bytes
      val isReadable = Files.isReadable(file)
      log.error(s"Unable to read image from file '$file'. Size: $size, readable: $isReadable.")
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

//  def cachedAction(result: Result) =
//    cache((req: RequestHeader) => req.path, 180.days)(Action(result))

  def fut[T](t: T) = Future.successful(t)
}
