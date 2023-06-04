package com.malliina.pics.http4s

import cats.effect.Async
import com.malliina.pics.http4s.PicsImplicits.*
import com.malliina.util.AppLogger
import org.http4s.Response
import com.malliina.http.ResponseException

import java.io.IOException
import scala.util.control.NonFatal

class ErrorHandler[F[_]: Async] extends BasicService[F]:
  private val log = AppLogger(getClass)

  def partial: PartialFunction[Throwable, F[Response[F]]] =
    case re: ResponseException =>
      val error = re.error
      log.error(s"HTTP ${error.code} for '${error.url}'. Body: '${error.response.asString}'.")
      serverError
    case ioe: IOException =>
      log.debug(s"Server IO error.", ioe)
      serverError
    case NonFatal(t) =>
      log.error(s"Server error.", t)
      serverError
