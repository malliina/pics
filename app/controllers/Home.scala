package controllers

import com.malliina.pics.{ContentType, DataStream, Key, PicFiles}
import com.malliina.play.controllers.OAuthSecured
import org.apache.commons.io.FilenameUtils
import play.api.Logger
import play.api.cache.Cached
import play.api.data.Form
import play.api.data.Forms._
import play.api.http.{HeaderNames, HttpEntity, MimeTypes}
import play.api.libs.json.Json
import play.api.libs.ws.{StreamedResponse, WSClient}
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.Future

case class UserFeedback(message: String, isSuccess: Boolean)

object UserFeedback {
  val Message = "message"
  val ErrorMessage = "error-message"

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
      routes.Home.thumb(key))
}

class Home(files: PicFiles, oauth: Admin, cache: Cached, wsClient: WSClient)
  extends OAuthSecured(oauth, oauth.mat) {

  private val log = Logger(getClass)
  val binaryContentType = ContentType(MimeTypes.BINARY)

  val CreatedKey = "created"
  val XKey = "X-Key"
  val Message = "message"
  val Reason = "reason"
  val KeyKey = "key"

  val deleteForm: Form[Key] = Form(mapping(KeyKey -> nonEmptyText)(Key.apply)(Key.unapply))

  def drop = authAction { req =>
    val created = req.flash.get(CreatedKey).map(k => KeyEntry(Key(k)))
    Ok(AppTags.drop(created, req.user))
  }

  def list = authAction { req =>
    val keys = files.load(0, 100)
    val entries = keys map { key => KeyEntry(key) }
    val feedback = UserFeedback.forFlash(req.flash)
    Ok(AppTags.pics(entries, feedback, req.user))
  }

  def pic(key: Key) = Action {
    files.find(key).fold(keyNotFound(key))(streamData)
  }

  def thumb(key: Key) = authActionAsync { _ =>
    files.find(key).fold(Future.successful(keyNotFound(key))) { stream =>
      val contentType = stream.contentType
      val isImage = contentType.exists(_.contentType startsWith "image")
      if (isImage) Future.successful(streamData(stream))
      else placeholderResult
    }
  }

  def delete = authenticatedLogged(_ => deleteNoAuth)

  private def deleteNoAuth = Action(BodyParsers.parse.form(deleteForm)) { req =>
    removeKey(req.body)
  }

  def remove(key: Key) = authAction { _ =>
    removeKey(key)
  }

  def removeKey(key: Key): Result = {
    val redir = Redirect(routes.Home.list())
    if (files contains key) {
      files remove key
      log info s"Removed $key"
      redir.flashing(UserFeedback.Message -> s"Deleted $key")
    } else {
      redir.flashing(UserFeedback.ErrorMessage -> s"Key not found: $key")
    }
  }

  def put = authenticatedLogged { _ =>
    putNoAuth
  }

  private def putNoAuth = Action(BodyParsers.parse.multipartFormData) { req =>
    req.body.files.headOption map { file =>
      val key = Key.randomish()
      val ext = FilenameUtils.getExtension(file.filename)
      val tempFile = file.ref.file
      val tempPath = tempFile.toPath
      val name = tempPath.getFileName.toString
      val renamedFile = tempPath resolveSibling s"$name.$ext"
      tempFile renameTo renamedFile.toFile
      files.put(key, renamedFile)
      val url = routes.Home.pic(key)
      log info s"Saved file ${file.filename} as $key"
      //Redirect(routes.Home.drop()).flashing(CreatedKey -> s"$url")
      Accepted(Json.obj(Message -> s"Created $url")).withHeaders(
        HeaderNames.LOCATION -> url.toString,
        XKey -> key.key
      )
    } getOrElse {
      badRequest("File missing")
    }
  }

  def logout = authAction(_ => oauth.ejectWith(oauth.logoutMessage).withNewSession)

  def eject = logged(Action(req => Ok(AppTags.eject(req.flash.get(oauth.messageKey)))))

  def streamData(stream: DataStream) =
    Ok.sendEntity(
      HttpEntity.Streamed(
        stream.source,
        stream.contentLength.map(_.toBytes),
        stream.contentType.map(_.contentType))
    )


  def placeholderResult = proxyResult("https://placehold.it/400x400")

  def proxyResult(url: String): Future[Result] = {
    // Make the request
    wsClient.url(url).withMethod("GET").stream().map {
      case StreamedResponse(response, body) =>

        // Check that the response was successful
        if (response.status == 200) {

          // Get the content type
          val contentType = response.headers.get(HeaderNames.CONTENT_TYPE).flatMap(_.headOption)
            .getOrElse(MimeTypes.BINARY)

          // If there's a content length, send that, otherwise return the body chunked
          response.headers.get(HeaderNames.CONTENT_LENGTH) match {
            case Some(Seq(length)) =>
              Ok.sendEntity(HttpEntity.Streamed(body, Some(length.toLong), Some(contentType)))
            case _ =>
              Ok.chunked(body).as(contentType)
          }
        } else {
          badGateway(s"A gateway server returned an unexpected response code")
        }
    }
  }

  def keyNotFound(key: Key) = onNotFound(s"Not found: $key")

  def onNotFound(message: String) = NotFound(Json.obj(Message -> message))

  def badGateway(reason: String) = BadGateway(reasonJson(reason))

  def badRequest(reason: String) = BadRequest(reasonJson(reason))

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
