package com.malliina.pics

import java.util.concurrent.CompletableFuture

import cats.effect.{Async, IO}
import cats.syntax.all.*

import scala.concurrent.{Future, Promise}

package object s3:
  implicit class CFOps[T](cf: CompletableFuture[T]):
    def io[F[_]: Async]: F[T] = asIO(cf)

  private def asFuture[T](cf: CompletableFuture[T]): Future[T] =
    val p = Promise[T]()
    cf.whenComplete((r, t) => Option(t).fold(p.success(r))(t => p.failure(t)))
    p.future

  private def asIO[F[_]: Async, T](cf: CompletableFuture[T]): F[T] = Async[F].async_ { cb =>
    cf.whenComplete((r, t) => Option(t).fold(cb(Right(r)))(t => cb(Left(t))))
  }
