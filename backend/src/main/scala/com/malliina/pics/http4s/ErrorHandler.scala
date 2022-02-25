package com.malliina.pics.http4s

import cats.Monad
import cats.effect.Async
import com.malliina.pics.http4s.PicsImplicits.*
import com.malliina.util.AppLogger
import org.http4s.headers.{Connection, `Content-Length`}
import org.http4s.{Headers, Request, Response, Status}
import org.typelevel.ci.CIStringSyntax
import com.malliina.http.ResponseException
import org.http4s.Status.InternalServerError

import scala.util.control.NonFatal

class ErrorHandler[F[_]: Async] extends BasicService[F]:
  private val log = AppLogger(getClass)

  def partial: PartialFunction[Throwable, F[Response[F]]] =
    case re: ResponseException =>
      val error = re.error
      log.error(s"HTTP ${error.code} for '${error.url}'. Body: '${error.response.asString}'.")
      serverError
    case NonFatal(t) =>
      log.error(s"Server error.", t)
      serverError

//  def apply[F[_], G[_]](implicit
//    F: Monad[F]
//  ): Request[G] => PartialFunction[Throwable, F[Response[G]]] =
//    req => {
//      case re: ResponseException =>
//        val error = re.error
//        log.error(s"HTTP ${error.code} for '${error.url}'. Body: '${error.response.asString}'.")
//        internalServerError(req)
//      case NonFatal(t) =>
//        log.error(s"Server error: ${req.method} ${req.pathInfo}. Exception $t", t)
//        internalServerError(req)
//    }

//  private def internalServerError[F[_], G[_]](
//    req: Request[G]
//  )(implicit F: Monad[F]): F[Response[G]] =
//    F.pure(
//      Response(
//        Status.InternalServerError,
//        req.httpVersion,
//        Headers(Connection(ci"close"), `Content-Length`.zero)
//      )
//    )
