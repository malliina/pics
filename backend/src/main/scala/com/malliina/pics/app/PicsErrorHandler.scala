package com.malliina.pics.app

import com.malliina.pics.Errors
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

object PicsErrorHandler extends PicsErrorHandler

class PicsErrorHandler extends HttpErrorHandler {
  private val log = Logger(getClass)

  override def onClientError(
    request: RequestHeader,
    statusCode: Int,
    message: String
  ): Future[Result] = {
    log.warn(s"Client error with status $statusCode for $request: '$message'.")
    fut(Status(statusCode)(Errors.single(message)))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    log.error(s"Server error for $request.", exception)
    fut(Status(500)(Errors.single("A server error occurred.")))
  }

  def fut[T](f: T) = Future.successful(f)
}
