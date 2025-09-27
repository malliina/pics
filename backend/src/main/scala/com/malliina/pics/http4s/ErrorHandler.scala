package com.malliina.pics.http4s

import cats.effect.Async
import com.malliina.http.ResponseException
import com.malliina.pics.KeyNotFound
import com.malliina.util.AppLogger
import org.http4s.Response

import java.io.IOException
import scala.util.control.NonFatal

class ErrorHandler[F[_]: Async] extends PicsBasicService[F]:
  private val log = AppLogger(getClass)

  def partial: PartialFunction[Throwable, F[Response[F]]] =
    case knf: KeyNotFound =>
      log.info(s"Key not found: '${knf.key}'.")
      notFoundWith("Key not found.")
    case re: ResponseException =>
      val error = re.error
      log.error(s"HTTP ${error.code} for '${error.url}'.")
      serverError
    case ioe: IOException =>
      log.debug(s"Server IO error.", ioe)
      serverError
    case NonFatal(t) =>
      log.error(s"Server error.", t)
      serverError
