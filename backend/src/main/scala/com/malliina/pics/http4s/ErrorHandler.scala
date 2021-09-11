package com.malliina.pics.http4s

import cats.Monad
import com.malliina.pics.http4s.PicsImplicits._
import com.malliina.util.AppLogger
import org.http4s.headers.{Connection, `Content-Length`}
import org.http4s.{Headers, Request, Response, Status}
import org.typelevel.ci.CIStringSyntax

import scala.util.control.NonFatal

object ErrorHandler {
  private val log = AppLogger(getClass)

  def apply[F[_], G[_]](implicit
    F: Monad[F]
  ): Request[G] => PartialFunction[Throwable, F[Response[G]]] =
    req => { case NonFatal(t) =>
      log.error(s"Server error: ${req.method} ${req.pathInfo}. Exception $t", t)
      F.pure(
        Response(
          Status.InternalServerError,
          req.httpVersion,
          Headers(Connection(ci"close"), `Content-Length`.zero)
        )
      )
    }
}
